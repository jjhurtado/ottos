package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemittanceEventRepository extends JpaRepository<RemittanceEvent, UUID> {

    List<RemittanceEvent> findByRemittanceIdOrderByOccurredAtAsc(UUID remittanceId);

    /** The latest status change up to a moment: whoever it left as courier was assigned at that moment. */
    Optional<RemittanceEvent> findFirstByRemittanceIdAndTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
            UUID remittanceId, EventType type, Instant at);
}
