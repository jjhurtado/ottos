package org.jobits.ottos.remittances.application;

import org.jobits.ottos.branches.ZoneService;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceStatusRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds {@link RemittanceView}s, resolving names once per batch and hiding what the viewer may not see. */
@Component
class RemittanceViews {

    private final RemittanceStatusRepository statuses;
    private final CustomerService customers;
    private final StaffDirectoryService staff;
    private final ZoneService zones;
    private final Clock clock;

    RemittanceViews(RemittanceStatusRepository statuses, CustomerService customers, StaffDirectoryService staff, ZoneService zones,
                    Clock clock) {
        this.statuses = statuses;
        this.customers = customers;
        this.staff = staff;
        this.zones = zones;
        this.clock = clock;
    }

    RemittanceView of(Remittance r, Viewer viewer) {
        return of(List.of(r), viewer).get(0);
    }

    List<RemittanceView> of(List<Remittance> remittances, Viewer viewer) {
        Map<String, RemittanceStatus> statusByCode = statuses.findAll().stream()
                .collect(Collectors.toMap(RemittanceStatus::getCode, Function.identity()));
        Map<String, String> municipalityNames = zones.municipalities(null).stream()
                .collect(Collectors.toMap(ZoneService.MunicipalityInfo::code, ZoneService.MunicipalityInfo::name));
        Map<UUID, Optional<CustomerInfo>> customerById = new HashMap<>();
        Map<UUID, Optional<String>> courierNames = new HashMap<>();
        LocalDate today = LocalDate.now(clock);

        return remittances.stream().map(r -> {
            RemittanceStatus status = statusByCode.get(r.getStatus());
            boolean financials = viewer.seesFinancials(r);
            Optional<CustomerInfo> customer = viewer.has(Viewer.READ)
                    ? customerById.computeIfAbsent(r.getCustomerId(), customers::find)
                    : Optional.empty();
            String courierName = r.getCourierId() == null ? null
                    : courierNames.computeIfAbsent(r.getCourierId(),
                            id -> staff.find(id).map(StaffDirectoryService.StaffMember::name)).orElse(null);
            return new RemittanceView(
                    r.getId(), r.getCode(), r.getType(), r.getStatus(), status.getName(),
                    r.getCustomerId(),
                    customer.map(CustomerInfo::fullName).orElse(null),
                    customer.map(CustomerInfo::phone).orElse(null),
                    r.getBeneficiaryId(), r.getBeneficiaryName(), r.getBeneficiaryPhone(),
                    r.getBeneficiaryAlternatePhone(), r.getBeneficiaryAddress(), r.getBeneficiaryMunicipalityCode(),
                    municipalityNames.get(r.getBeneficiaryMunicipalityCode()), r.getBeneficiaryReference(),
                    r.getSourceCurrency(), r.getTargetCurrency(),
                    r.getAmount(),
                    financials ? r.getFee() : null,
                    financials ? r.getTotal() : null,
                    financials ? r.getRate() : null,
                    financials ? r.getAmountToDeliver() : null,
                    r.cashAmount(), r.cashCurrency(),
                    viewer.seesPin() ? r.getPin() : null,
                    r.getCourierId(), courierName,
                    r.getExpectedDate(),
                    !status.isFinalStatus() && r.getExpectedDate().isBefore(today),
                    r.getPostponements(), incident(r), r.getNotes(), r.getCreatedAt(), r.getCreatedBy(),
                    r.getCompletedAt());
        }).toList();
    }

    private static RemittanceView.OpenIncident incident(Remittance r) {
        return r.getOpenIncidentReason() == null ? null : new RemittanceView.OpenIncident(
                r.getOpenIncidentReason(), r.getOpenIncidentNote(), r.getOpenIncidentAt(), r.getOpenIncidentBy());
    }
}
