package org.jobits.ottos.payments.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashAccountRepository extends JpaRepository<CashAccount, UUID> {

    Optional<CashAccount> findByKey(String key);

    /**
     * Creates the account unless it exists; safe when two transactions create the same one at once. The id is new,
     * so the only conflict possible is on account_key (H2 does not accept a conflict target).
     */
    @Modifying
    @Query(value = """
            INSERT INTO cash_accounts (id, account_key, type, owner_id, currency, created_at)
            VALUES (:id, :key, :type, :ownerId, :currency, :createdAt)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(UUID id, String key, String type, UUID ownerId, String currency, Instant createdAt);

    List<CashAccount> findByTypeOrderByCurrency(AccountType type);

    List<CashAccount> findByTypeAndOwnerIdOrderByCurrency(AccountType type, UUID ownerId);
}
