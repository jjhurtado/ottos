package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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

    List<Remittance> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Remittance> findByBeneficiaryIdOrderByCreatedAtDesc(UUID beneficiaryId);
}
