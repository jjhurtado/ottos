package org.jobits.ottos.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeRuleRepository extends JpaRepository<FeeRule, UUID> {

    Optional<FeeRule> findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(String corridorCode, Instant at);

    List<FeeRule> findByCorridorCodeOrderByValidFromDesc(String corridorCode);
}
