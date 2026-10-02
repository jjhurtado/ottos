package org.jobits.ottos.payments.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashAccountRepository extends JpaRepository<CashAccount, UUID> {

    Optional<CashAccount> findByKey(String key);

    List<CashAccount> findByTypeOrderByCurrency(AccountType type);

    List<CashAccount> findByTypeAndOwnerIdOrderByCurrency(AccountType type, UUID ownerId);
}
