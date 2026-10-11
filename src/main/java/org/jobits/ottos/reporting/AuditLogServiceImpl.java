package org.jobits.ottos.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.reporting.domain.AuditEntry;
import org.jobits.ottos.reporting.domain.AuditEntryRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Implementation of {@link AuditLogService}. */
@Service
class AuditLogServiceImpl implements AuditLogService {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {
    };

    private final AuditEntryRepository entries;
    private final StaffDirectoryService staff;
    private final ObjectMapper json;
    private final Clock clock;

    AuditLogServiceImpl(AuditEntryRepository entries, StaffDirectoryService staff, ObjectMapper json, Clock clock) {
        this.entries = entries;
        this.staff = staff;
        this.json = json;
        this.clock = clock;
    }

    /** Runs in the publisher's transaction: if the change rolls back, so does its entry. */
    @EventListener
    void on(AuditEvent event) {
        entries.save(new AuditEntry(event.occurredAt(), event.actorId(), event.action(), event.targetType(),
                event.targetId(), event.targetLabel(), write(event.details())));
    }

    @Override
    @Transactional(readOnly = true)
    public AuditPage search(AuditFilter filter, int page, int size) {
        Page<AuditEntry> found = entries.findAll(specification(filter),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        Map<UUID, String> names = new HashMap<>();
        List<AuditEntryView> items = found.getContent().stream()
                .map(e -> new AuditEntryView(e.getId(), e.getOccurredAt(), e.getActorId(), actorName(e, names),
                        e.getAction(), e.getTargetType(), e.getTargetId(), e.getTargetLabel(), read(e.getDetails())))
                .toList();
        return new AuditPage(items, found.getNumber(), found.getSize(), found.getTotalElements());
    }

    private Specification<AuditEntry> specification(AuditFilter f) {
        ZoneId zone = clock.getZone();
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.targetType() != null) {
                where.add(cb.equal(root.get("targetType"), f.targetType()));
            }
            if (f.targetId() != null) {
                where.add(cb.equal(root.get("targetId"), f.targetId()));
            }
            if (f.actorId() != null) {
                where.add(cb.equal(root.get("actorId"), f.actorId()));
            }
            if (f.action() != null) {
                where.add(cb.equal(root.get("action"), f.action()));
            }
            if (f.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from().atStartOfDay(zone).toInstant()));
            }
            if (f.to() != null) {
                where.add(cb.lessThan(root.get("occurredAt"), f.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private String actorName(AuditEntry e, Map<UUID, String> names) {
        if (e.getActorId() == null) {
            return null;
        }
        return names.computeIfAbsent(e.getActorId(),
                id -> staff.find(id).map(StaffDirectoryService.StaffMember::name).orElse(null));
    }

    private String write(Map<String, Object> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details are not serializable", e);
        }
    }

    private Map<String, Object> read(String details) {
        try {
            return json.readValue(details, DETAILS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored audit details are not valid JSON", e);
        }
    }
}
