package org.jobits.ottos.payments.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.payments.application.CashLedger;
import org.jobits.ottos.payments.application.CashLedger.Balance;
import org.jobits.ottos.payments.application.CashLedger.CourierCash;
import org.jobits.ottos.payments.application.CashLedger.CourierStatement;
import org.jobits.ottos.payments.application.CashLedger.MovementView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash")
@Tag(name = "Cash")
@SecurityRequirement(name = "bearer")
class CashController {

    private final CashLedger ledger;

    CashController(CashLedger ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/business")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "Business cash box balance per currency")
    List<Balance> business() {
        return ledger.businessBalances();
    }

    @GetMapping("/business/movements")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "Latest 100 movements of the business cash box")
    List<MovementView> businessMovements() {
        return ledger.businessMovements();
    }

    @GetMapping("/couriers")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "Cash carried by each courier; negative=true flags a courier below zero")
    List<CourierCash> couriers() {
        return ledger.couriers();
    }

    @GetMapping("/couriers/{courierId}")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "A courier's balance and latest 100 movements")
    CourierStatement courier(@PathVariable UUID courierId) {
        return ledger.courierStatement(courierId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('cash:read-own')")
    @Operation(summary = "My cash as a courier: balance and latest 100 movements")
    CourierStatement me(@AuthenticationPrincipal Jwt jwt) {
        return ledger.courierStatement(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/couriers/{courierId}/funding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:write')")
    @Operation(summary = "Hand cash from the business box to a courier")
    MovementView fund(@PathVariable UUID courierId, @Valid @RequestBody CashRequest request,
                      @AuthenticationPrincipal Jwt jwt) {
        return ledger.fundCourier(courierId, request.amount(), request.currency(), request.note(),
                UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/couriers/{courierId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:write')")
    @Operation(summary = "Register cash a courier gave back to the business box")
    MovementView courierReturn(@PathVariable UUID courierId, @Valid @RequestBody CashRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return ledger.courierReturn(courierId, request.amount(), request.currency(), request.note(),
                UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/business/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:adjust')")
    @Operation(summary = "Put money into the business box (capital, CUP bought…); the note is required")
    MovementView deposit(@Valid @RequestBody AdjustmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ledger.deposit(request.amount(), request.currency(), request.note(), UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/business/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:adjust')")
    @Operation(summary = "Take money out of the business box (expenses, USD sold…); the note is required")
    MovementView withdraw(@Valid @RequestBody AdjustmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ledger.withdraw(request.amount(), request.currency(), request.note(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/remittances/{remittanceId}/movements")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "Cash movements caused by a remittance")
    List<MovementView> ofRemittance(@PathVariable UUID remittanceId) {
        return ledger.movementsOfRemittance(remittanceId);
    }

    record CashRequest(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
                       @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
                       @Size(max = 1000) String note) {
    }

    record AdjustmentRequest(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
                             @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
                             @NotBlank @Size(max = 1000) String note) {
    }
}
