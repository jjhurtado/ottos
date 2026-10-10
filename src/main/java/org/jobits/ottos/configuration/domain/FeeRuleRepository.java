package org.jobits.ottos.configuration.domain;

import org.jobits.ottos.configuration.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRuleRepository extends JpaRepository<FeeRule, UUID> {

    Optional<FeeRule> findFirstByCorridorCodeAndRemittanceTypeAndValidFromLessThanEqualOrderByValidFromDesc(
            String corridorCode, ServiceType remittanceType, Instant at);

    List<FeeRule> findByCorridorCodeOrderByValidFromDesc(String corridorCode);

    List<FeeRule> findByCorridorCodeAndRemittanceTypeOrderByValidFromDesc(String corridorCode,
                                                                     ServiceType remittanceType);
}
