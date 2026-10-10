package org.jobits.ottos.beneficiaries;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.Phones;
import org.jobits.ottos.beneficiaries.domain.Beneficiary;
import org.jobits.ottos.beneficiaries.domain.BeneficiaryRepository;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiary;
import org.jobits.ottos.beneficiaries.domain.CustomerBeneficiaryRepository;
import org.jobits.ottos.branches.ZoneService;
import org.jobits.ottos.branches.ZoneService.MunicipalityInfo;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
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

/** Implementation of {@link BeneficiaryService}. */
@Service
class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final int SEARCH_LIMIT = 50;

    private final BeneficiaryRepository beneficiaries;
    private final CustomerBeneficiaryRepository links;
    private final CustomerService customers;
    private final ZoneService zones;
    private final Clock clock;

    BeneficiaryServiceImpl(BeneficiaryRepository beneficiaries, CustomerBeneficiaryRepository links, CustomerService customers,
                  ZoneService zones, Clock clock) {
        this.beneficiaries = beneficiaries;
        this.links = links;
        this.customers = customers;
        this.zones = zones;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BeneficiaryInfo> find(UUID id) {
        return beneficiaries.findById(id).map(this::info);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLinked(UUID customerId, UUID beneficiaryId) {
        return links.existsById(new CustomerBeneficiary.Key(customerId, beneficiaryId));
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryDetail get(UUID id) {
        Beneficiary beneficiary = beneficiaries.findById(id).orElseThrow(BeneficiaryServiceImpl::notFound);
        List<CustomerInfo> senders = links.findByKeyBeneficiaryId(id).stream()
                .map(l -> customers.find(l.getCustomerId()))
                .flatMap(Optional::stream)
                .toList();
        return new BeneficiaryDetail(info(beneficiary), senders);
    }

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryInfo> ofCustomer(UUID customerId) {
        customers.get(customerId);
        return infos(beneficiaries.findByCustomer(customerId));
    }

    @Override
    @Transactional
    public BeneficiaryInfo create(UUID customerId, Beneficiary.Details details, UUID actorId) {
        customers.get(customerId);
        Instant now = clock.instant();
        Beneficiary beneficiary = beneficiaries.save(new Beneficiary(validate(details), now, actorId));
        links.save(new CustomerBeneficiary(customerId, beneficiary.getId(), now));
        return info(beneficiary);
    }

    @Override
    @Transactional
    public BeneficiaryInfo update(UUID id, Beneficiary.Details details) {
        Beneficiary beneficiary = beneficiaries.findById(id).orElseThrow(BeneficiaryServiceImpl::notFound);
        beneficiary.update(validate(details));
        return info(beneficiary);
    }

    @Override
    @Transactional
    public void link(UUID customerId, UUID beneficiaryId) {
        customers.get(customerId);
        beneficiaries.findById(beneficiaryId).orElseThrow(BeneficiaryServiceImpl::notFound);
        if (!isLinked(customerId, beneficiaryId)) {
            links.save(new CustomerBeneficiary(customerId, beneficiaryId, clock.instant()));
        }
    }

    @Override
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
}
