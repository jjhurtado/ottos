package org.jobits.ottos.remittances;

import org.jobits.ottos.branches.ZoneService;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceStatusRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds the {@link SalesDetail} of a period: one query for the remittances and one per kind of name. */
@Component
class SalesLines {

    private final RemittanceRepository remittances;
    private final RemittanceStatusRepository statuses;
    private final ZoneService zones;
    private final StaffDirectoryService staff;

    SalesLines(RemittanceRepository remittances, RemittanceStatusRepository statuses, ZoneService zones,
               StaffDirectoryService staff) {
        this.remittances = remittances;
        this.statuses = statuses;
        this.zones = zones;
        this.staff = staff;
    }

    /** Remittances registered in [from, to); "completed" in the counts means completed before to. */
    SalesDetail of(Instant from, Instant to) {
        List<Remittance> found = remittances.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscCodeAsc(
                from, to);
        Map<String, String> statusNames = statuses.findAll().stream()
                .collect(Collectors.toMap(RemittanceStatus::getCode, RemittanceStatus::getName));
        Map<String, String> municipalityNames = zones.municipalities(null).stream()
                .collect(Collectors.toMap(ZoneService.MunicipalityInfo::code, ZoneService.MunicipalityInfo::name));
        Set<UUID> staffIds = found.stream()
                .flatMap(r -> Stream.of(r.getCourierId(), r.getVoidedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> staffNames = staffIds.isEmpty() ? Map.of() : staff.names(staffIds);

        List<SalesLine> lines = found.stream().map(r -> {
            SaleFigures figures = SaleFigures.of(r);
            return new SalesLine(r.getId(), r.getCode(), r.getType(), r.getStatus(), statusNames.get(r.getStatus()),
                    r.getCreatedAt(), r.getExpectedDate(), r.getCompletedAt(), r.isCompletedFromBusiness(),
                    r.getBeneficiaryMunicipalityCode(), municipalityNames.get(r.getBeneficiaryMunicipalityCode()),
                    r.getCourierId() == null ? null : staffNames.get(r.getCourierId()),
                    r.getSourceCurrency(), r.getTargetCurrency(), r.getAmount(), r.getFee(), r.getTotal(), r.getRate(),
                    r.getAmountToDeliver(), figures.collected(), figures.deliveredCurrency(), r.isOnTheHouse(),
                    r.getWaivedFee(), r.getOpenIncidentReason() != null,
                    r.isVoided() ? new RemittanceView.Voided(r.getVoidedAt(), r.getVoidedBy(),
                            staffNames.get(r.getVoidedBy()), r.getVoidReason()) : null);
        }).toList();
        List<Remittance> counted = found.stream().filter(r -> !r.isVoided()).toList();
        return new SalesDetail(found.size() - counted.size(), SalesSummaries.of(counted, to).remittances(), lines);
    }
}
