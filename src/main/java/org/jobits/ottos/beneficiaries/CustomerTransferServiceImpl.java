package org.jobits.ottos.beneficiaries;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.Phones;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryData;
import org.jobits.ottos.beneficiaries.domain.Beneficiary;
import org.jobits.ottos.beneficiaries.domain.BeneficiaryRepository;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiary;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiaryRepository;
import org.jobits.ottos.branches.ZoneService;
import org.jobits.ottos.branches.ZoneService.MunicipalityInfo;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerData;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of {@link CustomerTransferService}. */
@Service
class CustomerTransferServiceImpl implements CustomerTransferService {

    private final CustomerService customers;
    private final BeneficiaryService beneficiaryService;
    private final BeneficiaryRepository beneficiaries;
    private final CustomerBeneficiaryRepository links;
    private final ZoneService zones;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    CustomerTransferServiceImpl(CustomerService customers, BeneficiaryService beneficiaryService,
                                BeneficiaryRepository beneficiaries, CustomerBeneficiaryRepository links,
                                ZoneService zones, PlatformTransactionManager transactionManager,
                                ApplicationEventPublisher publisher, Clock clock) {
        this.customers = customers;
        this.beneficiaryService = beneficiaryService;
        this.beneficiaries = beneficiaries;
        this.links = links;
        this.zones = zones;
        this.transactions = new TransactionTemplate(transactionManager);
        this.publisher = publisher;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- export

    @Override
    @Transactional
    public CustomerExport exportAll(UUID actorId) {
        Map<String, MunicipalityInfo> municipalities = zones.provinces().stream()
                .flatMap(p -> zones.municipalities(p.code()).stream())
                .collect(Collectors.toMap(MunicipalityInfo::code, Function.identity()));
        Map<UUID, Beneficiary> byId = beneficiaries.findAll().stream()
                .collect(Collectors.toMap(Beneficiary::getId, Function.identity()));
        Map<UUID, List<Beneficiary>> byCustomer = links.findAll().stream()
                .collect(Collectors.groupingBy(CustomerBeneficiary::getCustomerId,
                        Collectors.mapping(l -> byId.get(l.getBeneficiaryId()), Collectors.toList())));
        List<ExportedCustomer> exported = customers.all().stream()
                .map(c -> exported(c, byCustomer.getOrDefault(c.id(), List.of()), municipalities))
                .toList();
        Instant now = clock.instant();
        publisher.publishEvent(new AuditEvent("CUSTOMERS_EXPORTED", "CUSTOMERS", UUID.randomUUID().toString(),
                "export", actorId, now, Map.of("customers", exported.size())));
        return new CustomerExport(now, exported);
    }

    private static ExportedCustomer exported(CustomerInfo c, List<Beneficiary> linked,
                                             Map<String, MunicipalityInfo> municipalities) {
        List<ExportedBeneficiary> list = linked.stream()
                .sorted(Comparator.comparing(b -> b.details().fullName(), String.CASE_INSENSITIVE_ORDER))
                .map(b -> {
                    Beneficiary.Details d = b.details();
                    MunicipalityInfo m = municipalities.get(d.municipalityCode());
                    return new ExportedBeneficiary(b.getId(), d.fullName(), d.phone(), d.alternatePhone(), d.street(),
                            d.houseNumber(), d.betweenStreets(), d.neighborhood(), d.municipalityCode(),
                            m == null ? null : m.name(), d.reference(), d.documentNumber(), d.notes(), b.isActive());
                })
                .toList();
        return new ExportedCustomer(c.id(), c.fullName(), c.phone(), c.email(), c.documentType(), c.documentNumber(),
                c.address(), c.notes(), c.active(), c.createdAt(), list);
    }

    // ---------------------------------------------------------------- import

    @Override
    public ImportResult importCustomers(boolean dryRun, List<ImportCustomer> items, UUID actorId) {
        Municipalities municipalities = new Municipalities(zones);
        List<CustomerResult> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ImportCustomer item = items.get(i);
            int index = i;
            CustomerResult result;
            try {
                result = transactions.execute(status -> {
                    CustomerResult r = importOne(index, item, actorId, municipalities, dryRun);
                    if (dryRun) {
                        status.setRollbackOnly();
                    }
                    return r;
                });
            } catch (ApiException e) {
                result = new CustomerResult(index, item.ref(), ItemStatus.REJECTED, null, e.getCode(), e.getReason(),
                        List.of());
            }
            results.add(result);
        }
        Summary summary = summarize(results);
        if (!dryRun) {
            publisher.publishEvent(new AuditEvent("CUSTOMERS_IMPORTED", "CUSTOMERS", UUID.randomUUID().toString(),
                    "import", actorId, clock.instant(), summaryDetails(summary)));
        }
        return new ImportResult(dryRun, summary, results);
    }

    @Override
    public BeneficiaryImportResult importBeneficiaries(boolean dryRun, List<ImportBeneficiary> items, UUID actorId) {
        Municipalities municipalities = new Municipalities(zones);
        List<BeneficiaryResult> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ImportBeneficiary item = items.get(i);
            int index = i;
            results.add(transactions.execute(status -> {
                BeneficiaryResult r = importBeneficiary(index, item, null, actorId, municipalities, dryRun);
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return r;
            }));
        }
        Map<ItemStatus, Long> counts = results.stream()
                .collect(Collectors.groupingBy(BeneficiaryResult::status, Collectors.counting()));
        BeneficiarySummary summary = new BeneficiarySummary(counts.getOrDefault(ItemStatus.CREATED, 0L),
                counts.getOrDefault(ItemStatus.EXISTING, 0L), counts.getOrDefault(ItemStatus.REJECTED, 0L));
        if (!dryRun) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("beneficiariesCreated", summary.created());
            details.put("beneficiariesExisting", summary.existing());
            details.put("beneficiariesRejected", summary.rejected());
            publisher.publishEvent(new AuditEvent("BENEFICIARIES_IMPORTED", "BENEFICIARIES",
                    UUID.randomUUID().toString(), "import", actorId, clock.instant(), details));
        }
        return new BeneficiaryImportResult(dryRun, summary, results);
    }

    private CustomerResult importOne(int index, ImportCustomer item, UUID actorId, Municipalities municipalities,
                                     boolean dryRun) {
        CustomerData details = new CustomerData(
                required(item.fullName(), "fullName", 150), required(item.phone(), "phone", 30),
                optional(item.email(), "email", 255), optional(item.documentType(), "documentType", 20),
                optional(item.documentNumber(), "documentNumber", 50), optional(item.address(), "address", 255),
                optional(item.notes(), "notes", 1000));
        optional(item.ref(), "ref", 100);
        Optional<CustomerInfo> existing = customers.findIdentical(details);
        CustomerInfo customer = existing.orElseGet(() -> customers.register(details, actorId));
        ItemStatus status = existing.isPresent() ? ItemStatus.EXISTING : ItemStatus.CREATED;

        List<BeneficiaryResult> results = new ArrayList<>();
        List<ImportBeneficiary> list = item.beneficiaries() == null ? List.of() : item.beneficiaries();
        for (int i = 0; i < list.size(); i++) {
            results.add(importBeneficiary(i, list.get(i), customer.id(), actorId, municipalities, dryRun));
        }
        UUID id = dryRun && status == ItemStatus.CREATED ? null : customer.id();
        return new CustomerResult(index, item.ref(), status, id, null, null, results);
    }

    /**
     * customerId null: a beneficiary without a customer, nothing is linked. Rejections only reject this beneficiary. They are all checked here before calling BeneficiaryService: an
     * exception thrown from inside a transactional call would mark the customer's whole transaction for rollback.
     */
    private BeneficiaryResult importBeneficiary(int index, ImportBeneficiary item, UUID customerId, UUID actorId,
                                                Municipalities municipalities, boolean dryRun) {
        try {
            optional(item.ref(), "ref", 100);
            BeneficiaryData details = new BeneficiaryData(
                    required(item.fullName(), "fullName", 150), required(item.phone(), "phone", 30),
                    optional(item.alternatePhone(), "alternatePhone", 30), required(item.street(), "street", 150),
                    optional(item.houseNumber(), "houseNumber", 30), optional(item.betweenStreets(), "betweenStreets", 150),
                    optional(item.neighborhood(), "neighborhood", 100),
                    municipalities.code(required(item.municipality(), "municipality", 100)),
                    optional(item.reference(), "reference", 255), optional(item.documentNumber(), "documentNumber", 50),
                    optional(item.notes(), "notes", 1000));
            Optional<Beneficiary> existing = identical(details.toDetails());
            if (existing.isPresent()) {
                if (!existing.get().isActive()) {
                    throw ApiException.conflict("BENEFICIARY_INACTIVE", "The beneficiary was deleted");
                }
                UUID id = existing.get().getId();
                boolean link = customerId != null && !beneficiaryService.isLinked(customerId, id);
                if (link) {
                    beneficiaryService.link(customerId, id);
                }
                return new BeneficiaryResult(index, item.ref(), ItemStatus.EXISTING, id, link, null, null);
            }
            UUID created = customerId == null
                    ? beneficiaryService.create(details, actorId).id()
                    : beneficiaryService.create(customerId, details, actorId).id();
            return new BeneficiaryResult(index, item.ref(), ItemStatus.CREATED, dryRun ? null : created,
                    customerId != null, null, null);
        } catch (ApiException e) {
            return new BeneficiaryResult(index, item.ref(), ItemStatus.REJECTED, null, false, e.getCode(), e.getReason());
        }
    }

    /** Same normalized phone, and the same name, street, house number and municipality ignoring case and spaces. */
    private Optional<Beneficiary> identical(Beneficiary.Details d) {
        String phone = Phones.normalize(d.phone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone must contain digits");
        }
        return beneficiaries.findByPhone(phone).stream()
                .filter(b -> {
                    Beneficiary.Details e = b.details();
                    return same(e.fullName(), d.fullName()) && same(e.street(), d.street())
                            && same(e.houseNumber(), d.houseNumber()) && same(e.municipalityCode(), d.municipalityCode());
                })
                .findFirst();
    }

    private static Summary summarize(List<CustomerResult> results) {
        long cc = 0, ce = 0, cr = 0, bc = 0, be = 0, br = 0, links = 0;
        for (CustomerResult c : results) {
            switch (c.status()) {
                case CREATED -> cc++;
                case EXISTING -> ce++;
                case REJECTED -> cr++;
            }
            for (BeneficiaryResult b : c.beneficiaries()) {
                switch (b.status()) {
                    case CREATED -> bc++;
                    case EXISTING -> be++;
                    case REJECTED -> br++;
                }
                links += b.linked() ? 1 : 0;
            }
        }
        return new Summary(cc, ce, cr, bc, be, br, links);
    }

    private static Map<String, Object> summaryDetails(Summary s) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("customersCreated", s.customersCreated());
        details.put("customersExisting", s.customersExisting());
        details.put("customersRejected", s.customersRejected());
        details.put("beneficiariesCreated", s.beneficiariesCreated());
        details.put("beneficiariesExisting", s.beneficiariesExisting());
        details.put("beneficiariesRejected", s.beneficiariesRejected());
        details.put("linksCreated", s.linksCreated());
        return details;
    }

    private static String required(String value, String field, int max) {
        String v = optional(value, field, max);
        if (v == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", field + " is required");
        }
        return v;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw ApiException.badRequest("VALIDATION_FAILED", field + " must have at most " + max + " characters");
        }
        return v;
    }

    /** Equal ignoring case and repeated or outer spaces ("Ana  Pérez" is "ana pérez"); no value equals no value. */
    private static boolean same(String a, String b) {
        return squash(a).equalsIgnoreCase(squash(b));
    }

    private static String squash(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    /** Resolves a municipality given by code or by name (ignoring case and accents). */
    private static final class Municipalities {

        private final Map<String, String> codes = new HashMap<>();
        private final Map<String, String> byName = new HashMap<>();

        Municipalities(ZoneService zones) {
            zones.provinces().stream().flatMap(p -> zones.municipalities(p.code()).stream()).forEach(m -> {
                codes.put(m.code(), m.code());
                byName.put(key(m.name()), m.code());
            });
        }

        String code(String value) {
            String code = codes.getOrDefault(value, byName.get(key(value)));
            if (code == null) {
                throw ApiException.badRequest("UNKNOWN_MUNICIPALITY", "Unknown municipality " + value);
            }
            return code;
        }

        private static String key(String name) {
            return Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        }
    }
}
