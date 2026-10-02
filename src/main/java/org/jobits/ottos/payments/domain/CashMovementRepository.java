package org.jobits.ottos.payments.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    /** Money in minus money out. */
    @Query("""
            select coalesce(sum(case when m.toAccountId = :accountId then m.amount else -m.amount end), 0)
            from CashMovement m where m.toAccountId = :accountId or m.fromAccountId = :accountId""")
    BigDecimal balanceOf(UUID accountId);

    @Query("""
            select m from CashMovement m
            where m.toAccountId in :accountIds or m.fromAccountId in :accountIds
            order by m.occurredAt desc""")
    List<CashMovement> findTouching(Collection<UUID> accountIds, Pageable page);

    List<CashMovement> findByRemittanceIdOrderByOccurredAtAsc(UUID remittanceId);
}
