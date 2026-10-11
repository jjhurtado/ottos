package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncedActionRepository extends JpaRepository<SyncedAction, UUID> {
}
