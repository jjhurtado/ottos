package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemittanceRepository extends JpaRepository<Remittance, UUID>, JpaSpecificationExecutor<Remittance> {

    /** Open remittances per courier: rows of (courierId, count). */
    @Query("""
            select r.courierId, count(r) from Remittance r
            where r.courierId is not null and r.status not in :finalStatuses
            group by r.courierId""")
    List<Object[]> countOpenByCourier(Collection<String> finalStatuses);

    boolean existsByCode(String code);

    Optional<Remittance> findByCode(String code);

    Optional<Remittance> findByIdempotencyKey(String idempotencyKey);

    List<Remittance> findByCourierIdAndStatusNotInOrderByExpectedDateAsc(UUID courierId, Collection<String> statuses);

    /** Open remittances of a courier that changed after a moment, for the mobile app's incremental download. */
    List<Remittance> findByCourierIdAndStatusNotInAndUpdatedAtAfterOrderByExpectedDateAsc(
            UUID courierId, Collection<String> statuses, Instant since);

    /**
     * Remittances that changed after a moment and are no longer open and assigned to the user, although they were at
     * some point (reassigned to someone else, or closed): the mobile app removes them.
     */
    @Query("""
            select r.id from Remittance r
            where r.updatedAt > :since
              and (r.courierId is null or r.courierId <> :userId or r.status in :finalStatuses)
              and exists (select e.id from RemittanceEvent e where e.remittanceId = r.id and e.courierId = :userId)""")
    List<UUID> findNoLongerAssignedSince(UUID userId, Collection<String> finalStatuses, Instant since);

    List<Remittance> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /** Completions recorded in (after, until], oldest first. */
    List<Remittance> findByCompletionRecordedAtGreaterThanAndCompletionRecordedAtLessThanEqualOrderByCompletionRecordedAtAscIdAsc(
            Instant after, Instant until, Pageable page);

    /** Remittances registered in [from, to). */
    List<Remittance> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    List<Remittance> findByBeneficiaryIdOrderByCreatedAtDesc(UUID beneficiaryId);
}
