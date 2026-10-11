package org.jobits.ottos.sync.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.remittances.application.SyncActionType;
import org.jobits.ottos.remittances.application.Viewer;
import org.jobits.ottos.sync.SyncService;
import org.jobits.ottos.sync.SyncService.QueuedAction;
import org.jobits.ottos.sync.SyncService.SyncResult;
import org.jobits.ottos.sync.SyncService.SyncState;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Mobile sync")
@SecurityRequirement(name = "bearer")
class SyncController {

    static final int MAX_ACTIONS = 200;

    private final SyncService sync;

    SyncController(SyncService sync) {
        this.sync = sync;
    }

    @GetMapping("/state")
    @Operation(summary = "What the mobile app keeps for working offline: everything without since, or what changed after it")
    SyncState state(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
                    @AuthenticationPrincipal Jwt jwt) {
        return sync.state(since, viewer(jwt));
    }

    @PostMapping("/actions")
    @Operation(summary = "Apply the mobile app's queue of offline actions in order; one result per action")
    SyncResult actions(@Valid @RequestBody SyncRequest request, @AuthenticationPrincipal Jwt jwt) {
        Set<UUID> ids = new HashSet<>();
        for (ActionRequest action : request.actions()) {
            if (!ids.add(action.id())) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Action " + action.id() + " appears twice in the batch");
            }
        }
        List<QueuedAction> actions = request.actions().stream()
                .map(a -> {
                    Payload p = a.payload() == null ? Payload.EMPTY : a.payload();
                    return new QueuedAction(a.id(), a.type(), a.remittanceId(), a.occurredAt().toInstant(), p.note(),
                            p.pin(), p.strictPin(), p.newDate(), p.reason());
                })
                .toList();
        return sync.apply(request.deviceId(), actions, viewer(jwt));
    }

    private static Viewer viewer(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        return new Viewer(UUID.fromString(jwt.getSubject()), permissions == null ? Set.of() : Set.copyOf(permissions));
    }

    record SyncRequest(@Size(max = 100) String deviceId,
                       @NotNull @Size(max = MAX_ACTIONS) List<@Valid @NotNull ActionRequest> actions) {
    }

    record ActionRequest(@NotNull UUID id, @NotNull SyncActionType type, @NotNull UUID remittanceId,
                         @NotNull OffsetDateTime occurredAt, Payload payload) {
    }

    /** The fields of every action type; each type uses its own (see docs/contracts/offline-sync.md). */
    record Payload(String note, String pin, Boolean strictPin, LocalDate newDate, String reason) {

        static final Payload EMPTY = new Payload(null, null, null, null, null);
    }
}
