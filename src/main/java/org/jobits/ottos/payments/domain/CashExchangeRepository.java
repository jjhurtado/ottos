package org.jobits.ottos.payments.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CashExchangeRepository extends JpaRepository<CashExchange, UUID>, JpaSpecificationExecutor<CashExchange> {

    Optional<CashExchange> findByClientActionId(UUID clientActionId);
}
