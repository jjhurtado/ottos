package org.jobits.ottos.remittances.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.application.RemittanceFilter;
import org.jobits.ottos.remittances.application.RemittanceService;
import org.jobits.ottos.remittances.application.RemittanceService.EventView;
import org.jobits.ottos.remittances.application.RemittanceService.PageView;
import org.jobits.ottos.remittances.application.RemittanceView;
import org.jobits.ottos.remittances.application.Workflow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Remittances")
@SecurityRequirement(name = "bearer")
@Validated
class RemittanceController {

    private final RemittanceService remittances;
    private final Workflow workflow;

    RemittanceController(RemittanceService remittances, Workflow workflow) {
        this.remittances = remittances;
        this.workflow = workflow;
    }

    @PostMapping("/remittances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('remittances:create')")
    @Operation(summary = "Register a paid delivery or a pickup; repeating the Idempotency-Key returns the first one")
    RemittanceView register(@Valid @RequestBody NewRemittanceRequest request,
                            @Parameter(description = "Unique per request, e.g. a UUID; makes retries safe")
                            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
                            @AuthenticationPrincipal Jwt jwt) {
        var viewer = Viewers.from(jwt);
        try {
            return remittances.register(new RemittanceService.NewRemittance(request.type(), request.customerId(),
                    request.beneficiaryId(), request.amount(), request.targetCurrency(), request.notes(), idempotencyKey),
                    viewer);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key won the race: return its remittance.
            if (idempotencyKey == null) {
                throw e;
            }
            return remittances.findByIdempotencyKey(idempotencyKey, viewer).orElseThrow(() -> e);
        }
    }

    @GetMapping("/remittances")
    @PreAuthorize("hasAuthority('remittances:read')")
    @Operation(summary = "List remittances, newest first, with optional filters; late=true lists the overdue ones")
    PageView search(@RequestParam(required = false) String status,
                    @RequestParam(required = false) RemittanceType type,
                    @RequestParam(required = false) Boolean late,
                    @RequestParam(required = false) UUID courierId,
                    @RequestParam(required = false) UUID customerId,
                    @RequestParam(required = false) UUID beneficiaryId,
                    @RequestParam(required = false) String municipality,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @AuthenticationPrincipal Jwt jwt) {
        RemittanceFilter filter = new RemittanceFilter(status, type, late, courierId, customerId, beneficiaryId,
                municipality, from, to);
        return remittances.search(filter, page, size, Viewers.from(jwt));
    }

    @GetMapping("/couriers")
    @PreAuthorize("hasAuthority('remittances:assign')")
    @Operation(summary = "Staff who can be assigned remittances, by name, with their open remittances")
    List<RemittanceService.CourierView> couriers() {
        return remittances.couriers();
    }

    @GetMapping("/remittances/assigned")
    @PreAuthorize("hasAuthority('remittances:read-assigned')")
    @Operation(summary = "My open remittances (as courier), soonest expected date first")
    List<RemittanceView> assigned(@AuthenticationPrincipal Jwt jwt) {
        return remittances.assignedTo(Viewers.from(jwt));
    }

    @GetMapping("/remittances/{id}")
    @PreAuthorize("hasAnyAuthority('remittances:read', 'remittances:read-assigned')")
    @Operation(summary = "Get a remittance; couriers only see the ones assigned to them")
    RemittanceView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return remittances.get(id, Viewers.from(jwt));
    }

    @GetMapping("/remittances/code/{code}")
    @PreAuthorize("hasAuthority('remittances:read')")
    @Operation(summary = "Find a remittance by its tracking code, e.g. OT-7K3F9Q")
    RemittanceView byCode(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return remittances.getByCode(code, Viewers.from(jwt));
    }

    @GetMapping("/remittances/{id}/events")
    @PreAuthorize("hasAnyAuthority('remittances:read', 'remittances:read-assigned')")
    @Operation(summary = "History of a remittance: creation, status changes and postponements")
    List<EventView> events(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return remittances.history(id, Viewers.from(jwt));
    }

    @PostMapping("/remittances/{id}/assign")
    @PreAuthorize("hasAuthority('remittances:assign')")
    @Operation(summary = "Assign or reassign a courier")
    RemittanceView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request, @AuthenticationPrincipal Jwt jwt) {
        return remittances.assign(id, request.courierId(), request.note(), Viewers.from(jwt));
    }

    @PostMapping("/remittances/{id}/deliver")
    @PreAuthorize("hasAuthority('remittances:deliver')")
    @Operation(summary = "Mark as delivered (or collected, for a pickup) without PIN")
    RemittanceView deliver(@PathVariable UUID id, @Valid @RequestBody(required = false) NoteRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return remittances.complete(id, request == null ? null : request.note(), Viewers.from(jwt));
    }

    @PostMapping("/remittances/{id}/deliver-with-pin")
    @PreAuthorize("hasAuthority('remittances:deliver')")
    @Operation(summary = "Mark as delivered after checking the PIN the sender received")
    RemittanceView deliverWithPin(@PathVariable UUID id, @Valid @RequestBody PinRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return remittances.completeWithPin(id, request.pin(), request.note(), Viewers.from(jwt));
    }

    @PostMapping("/remittances/{id}/transitions")
    @PreAuthorize("hasAnyAuthority('remittances:read', 'remittances:read-assigned')")
    @Operation(summary = "Move to any status allowed by the workflow; the transition defines the permission needed")
    RemittanceView transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return remittances.transition(id, request.toStatus(), request.courierId(), request.note(), Viewers.from(jwt));
    }

    @PostMapping("/remittances/{id}/postpone")
    @PreAuthorize("hasAuthority('remittances:postpone')")
    @Operation(summary = "Move the expected date later, with a reason; couriers only on their own remittances")
    RemittanceView postpone(@PathVariable UUID id, @Valid @RequestBody PostponeRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        return remittances.postpone(id, request.newDate(), request.reason(), Viewers.from(jwt));
    }

    @GetMapping("/remittance-workflow")
    @PreAuthorize("hasAnyAuthority('remittances:read', 'remittances:read-assigned')")
    @Operation(summary = "Statuses and allowed transitions, as configured in the database")
    Workflow.WorkflowView workflow() {
        return workflow.describe();
    }

    record NewRemittanceRequest(@NotNull RemittanceType type,
                                @NotNull UUID customerId,
                                @NotNull UUID beneficiaryId,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount,
                                @NotNull @Pattern(regexp = "[A-Z]{3}") String targetCurrency,
                                @Size(max = 1000) String notes) {
    }

    record AssignRequest(@NotNull UUID courierId, @Size(max = 1000) String note) {
    }

    record NoteRequest(@Size(max = 1000) String note) {
    }

    record PinRequest(@NotBlank @Pattern(regexp = "\\d{6}") String pin, @Size(max = 1000) String note) {
    }

    record TransitionRequest(@NotBlank String toStatus, UUID courierId, @Size(max = 1000) String note) {
    }

    record PostponeRequest(@NotNull LocalDate newDate, @NotBlank @Size(max = 1000) String reason) {
    }
}
