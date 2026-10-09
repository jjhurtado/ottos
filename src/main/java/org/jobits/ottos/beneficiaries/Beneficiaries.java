package org.jobits.ottos.beneficiaries;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.Phones;
import org.jobits.ottos.beneficiaries.domain.Beneficiary;
import org.jobits.ottos.beneficiaries.domain.BeneficiaryRepository;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiary;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiaryRepository;
import org.jobits.ottos.branches.Zones;
import org.jobits.ottos.branches.Zones.MunicipalityInfo;
import org.jobits.ottos.customers.Customers;
import org.jobits.ottos.customers.Customers.CustomerInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Beneficiaries and their links to customers. Other modules use {@link #find(UUID)} and {@link #isLinked}. */
@Service
public class Beneficiaries {

    private static final int SEARCH_LIMIT = 50;

    private final BeneficiaryRepository beneficiaries;
    private final CustomerBeneficiaryRepository links;
    private final Customers customers;
    private final Zones zones;
    private final Clock clock;

    Beneficiaries(BeneficiaryRepository beneficiaries, CustomerBeneficiaryRepository links, Customers customers,
                  Zones zones, Clock clock) {
        this.beneficiaries = beneficiaries;
        this.links = links;
        this.customers = customers;
        this.zones = zones;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<BeneficiaryInfo> find(UUID id) {
        return beneficiaries.findById(id).map(this::info);
    }

    @Transactional(readOnly = true)
    public boolean isLinked(UUID customerId, UUID beneficiaryId) {
        return links.existsById(new CustomerBeneficiary.Key(customerId, beneficiaryId));
    }

    /** The beneficiary and the customers that send to them. */
    @Transactional(readOnly = true)
    public BeneficiaryDetail get(UUID id) {
        Beneficiary beneficiary = beneficiaries.findById(id).orElseThrow(Beneficiaries::notFound);
        List<CustomerInfo> senders = links.findByKeyBeneficiaryId(id).stream()
                .map(l -> customers.find(l.getCustomerId()))
                .flatMap(Optional::stream)
                .toList();
        return new BeneficiaryDetail(info(beneficiary), senders);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryInfo> search(String text) {
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT);
        List<Beneficiary> found;
        if (text == null || text.isBlank()) {
            found = beneficiaries.findAllByOrderByCreatedAtDesc(page);
        } else {
            String phone = Phones.normalize(text);
            found = phone == null
                    ? beneficiaries.searchByName(text.trim(), page)
                    : beneficiaries.searchByNameOrPhone(text.trim(), phone, page);
        }
        return infos(found);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryInfo> ofCustomer(UUID customerId) {
        customers.get(customerId);
        return infos(beneficiaries.findByCustomer(customerId));
    }

    /** Creates a beneficiary and links it to the customer who sends to them. */
    @Transactional
    public BeneficiaryInfo create(UUID customerId, Beneficiary.Details details, UUID actorId) {
        customers.get(customerId);
        Instant now = clock.instant();
        Beneficiary beneficiary = beneficiaries.save(new Beneficiary(validate(details), now, actorId));
        links.save(new CustomerBeneficiary(customerId, beneficiary.getId(), now));
        return info(beneficiary);
    }

    @Transactional
    public BeneficiaryInfo update(UUID id, Beneficiary.Details details) {
        Beneficiary beneficiary = beneficiaries.findById(id).orElseThrow(Beneficiaries::notFound);
        beneficiary.update(validate(details));
        return info(beneficiary);
    }

    /** Links an existing beneficiary to another customer. Linking twice is harmless. */
    @Transactional
    public void link(UUID customerId, UUID beneficiaryId) {
        customers.get(customerId);
        beneficiaries.findById(beneficiaryId).orElseThrow(Beneficiaries::notFound);
        if (!isLinked(customerId, beneficiaryId)) {
            links.save(new CustomerBeneficiary(customerId, beneficiaryId, clock.instant()));
        }
    }

    @Transactional
    public void unlink(UUID customerId, UUID beneficiaryId) {
        links.deleteById(new CustomerBeneficiary.Key(customerId, beneficiaryId));
    }

    private Beneficiary.Details validate(Beneficiary.Details d) {
        String phone = Phones.normalize(d.phone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone must contain digits");
        }
        if (zones.findMunicipality(d.municipalityCode()).isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_MUNICIPALITY", "Unknown municipality " + d.municipalityCode());
        }
        return new Beneficiary.Details(d.fullName().trim(), phone, Phones.normalize(d.alternatePhone()),
                d.street().trim(), blankToNull(d.houseNumber()), blankToNull(d.betweenStreets()),
                blankToNull(d.neighborhood()), d.municipalityCode(), blankToNull(d.reference()),
                blankToNull(d.documentNumber()), blankToNull(d.notes()));
    }

    private BeneficiaryInfo info(Beneficiary b) {
        MunicipalityInfo municipality = zones.findMunicipality(b.details().municipalityCode()).orElse(null);
        return BeneficiaryInfo.of(b, municipality);
    }

    private List<BeneficiaryInfo> infos(List<Beneficiary> found) {
        Map<String, MunicipalityInfo> municipalities = zones.municipalities(null).stream()
                .collect(Collectors.toMap(MunicipalityInfo::code, Function.identity()));
        return found.stream()
                .map(b -> BeneficiaryInfo.of(b, municipalities.get(b.details().municipalityCode())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound() {
        return ApiException.notFound("BENEFICIARY_NOT_FOUND", "Beneficiary not found");
    }

    public record BeneficiaryInfo(UUID id, String fullName, String phone, String alternatePhone, String street,
                                  String houseNumber, String betweenStreets, String neighborhood,
                                  String municipalityCode, String municipalityName, String provinceName,
                                  String reference, String documentNumber, String notes, boolean active,
                                  Instant createdAt) {

        static BeneficiaryInfo of(Beneficiary b, MunicipalityInfo municipality) {
            Beneficiary.Details d = b.details();
            return new BeneficiaryInfo(b.getId(), d.fullName(), d.phone(), d.alternatePhone(), d.street(),
                    d.houseNumber(), d.betweenStreets(), d.neighborhood(), d.municipalityCode(),
                    municipality == null ? null : municipality.name(),
                    municipality == null ? null : municipality.provinceName(),
                    d.reference(), d.documentNumber(), d.notes(), b.isActive(), b.getCreatedAt());
        }

        /** One-line address for receipts and delivery lists. */
        public String addressLine() {
            return Stream.of(
                            street + (houseNumber == null ? "" : " #" + houseNumber),
                            betweenStreets == null ? null : "e/ " + betweenStreets,
                            neighborhood, municipalityName, provinceName)
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(", "));
        }
    }

    public record BeneficiaryDetail(BeneficiaryInfo beneficiary, List<CustomerInfo> customers) {
    }
}
