package org.jobits.ottos.remittances;

import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.remittances.domain.Remittance;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** The query behind GET /remittances: each filter that is set narrows the list. */
final class RemittanceSpecifications {

    private RemittanceSpecifications() {
    }

    /**
     * @param finals final status codes (a late remittance is not in one)
     * @param today  today in the business timezone
     * @param zone   the business timezone, for the createdFrom/createdTo days
     */
    static Specification<Remittance> matching(RemittanceFilter f, List<String> finals, LocalDate today, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.type() != null) {
                where.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.courierId() != null) {
                where.add(cb.equal(root.get("courierId"), f.courierId()));
            }
            if (f.customerId() != null) {
                where.add(cb.equal(root.get("customerId"), f.customerId()));
            }
            if (f.beneficiaryId() != null) {
                where.add(cb.equal(root.get("beneficiaryId"), f.beneficiaryId()));
            }
            if (f.municipalityCode() != null) {
                where.add(cb.equal(root.get("beneficiaryMunicipalityCode"), f.municipalityCode()));
            }
            if (f.createdFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.createdFrom().atStartOfDay(zone).toInstant()));
            }
            if (f.createdTo() != null) {
                where.add(cb.lessThan(root.get("createdAt"), f.createdTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (f.review() != null) {
                where.add(f.review() ? cb.isNotNull(root.get("reviewReason")) : cb.isNull(root.get("reviewReason")));
            }
            if (f.voided() != null) {
                where.add(f.voided() ? cb.isNotNull(root.get("voidedAt")) : cb.isNull(root.get("voidedAt")));
            }
            if (f.onTheHouse() != null) {
                where.add(cb.equal(root.get("onTheHouse"), f.onTheHouse()));
            }
            if (f.incident() != null) {
                where.add(f.incident() ? cb.isNotNull(root.get("openIncidentReason"))
                        : cb.isNull(root.get("openIncidentReason")));
            }
            if (f.late() != null) {
                Predicate late = cb.and(
                        cb.isNull(root.get("voidedAt")),
                        cb.lessThan(root.get("expectedDate"), today),
                        finals.isEmpty() ? cb.conjunction() : cb.not(root.get("status").in(finals)));
                where.add(f.late() ? late : cb.not(late));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
