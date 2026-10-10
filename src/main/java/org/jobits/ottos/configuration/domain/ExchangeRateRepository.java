package org.jobits.ottos.configuration.domain;

import org.jobits.ottos.configuration.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findFirstByCorridorCodeAndRemittanceTypeAndValidFromLessThanEqualOrderByValidFromDesc(
            String corridorCode, ServiceType remittanceType, Instant at);

    List<ExchangeRate> findByCorridorCodeOrderByValidFromDesc(String corridorCode);

    List<ExchangeRate> findByCorridorCodeAndRemittanceTypeOrderByValidFromDesc(String corridorCode,
                                                                          ServiceType remittanceType);
}
