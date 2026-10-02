package org.jobits.ottos.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findFirstByCorridorCodeAndValidFromLessThanEqualOrderByValidFromDesc(String corridorCode, Instant at);

    List<ExchangeRate> findByCorridorCodeOrderByValidFromDesc(String corridorCode);
}
