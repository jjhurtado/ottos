package org.jobits.ottos.configuration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorridorRepository extends JpaRepository<Corridor, String> {

    Optional<Corridor> findBySourceCurrencyAndTargetCurrencyAndActiveTrue(String sourceCurrency, String targetCurrency);

    List<Corridor> findAllByOrderByCode();
}
