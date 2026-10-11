package org.jobits.ottos.payments.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.payments.application.CashLedgerService;
import org.jobits.ottos.payments.application.CashLedgerService.Balance;
import org.jobits.ottos.payments.application.CashLedgerService.CourierCash;
import org.jobits.ottos.payments.application.CashLedgerService.CourierStatement;
import org.jobits.ottos.payments.application.CashLedgerService.ExchangePage;
import org.jobits.ottos.payments.application.CashLedgerService.ExchangeView;
import org.jobits.ottos.payments.application.CashLedgerService.MovementView;
import org.jobits.ottos.payments.application.CashLedgerService.NewExchange;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash")
@Validated
@Tag(name = "Cash")
@SecurityRequirement(name = "bearer")
class CashController {

    private final CashLedgerService ledger;

    CashController(CashLedgerService ledger) {
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

    @PostMapping("/me/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:exchange-own')")
    @Operation(summary = "Record a currency exchange of my own cash as a courier; rate is units of the other currency per 1 USD")
    ExchangeView exchangeMine(@Valid @RequestBody ExchangeRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID me = UUID.fromString(jwt.getSubject());
        return ledger.recordExchange(me, request.toNewExchange(), me);
    }

    @PostMapping("/couriers/{courierId}/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash:write')")
    @Operation(summary = "Record a currency exchange on behalf of a courier")
    ExchangeView exchangeForCourier(@PathVariable UUID courierId, @Valid @RequestBody ExchangeRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ledger.recordExchange(courierId, request.toNewExchange(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/exchanges")
    @PreAuthorize("hasAuthority('cash:read')")
    @Operation(summary = "Courier currency exchanges, newest first; review=true lists the ones pending review")
    ExchangePage exchanges(@RequestParam(required = false) UUID courierId,
                           @RequestParam(required = false) Boolean review,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ledger.exchanges(courierId, review, from, to, page, size);
    }

    @PostMapping("/exchanges/{id}/review/resolve")
    @PreAuthorize("hasAuthority('cash:review')")
    @Operation(summary = "Resolve an exchange flagged for review, with a note")
    ExchangeView resolveExchangeReview(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return ledger.resolveExchangeReview(id, request.note(), UUID.fromString(jwt.getSubject()));
    }

    /** rate: units of the other currency per 1 USD, in both directions. The server computes the amount received. */
    record ExchangeRequest(@NotNull @Pattern(regexp = "[A-Za-z]{3}") String fromCurrency,
                           @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal fromAmount,
                           @NotNull @Pattern(regexp = "[A-Za-z]{3}") String toCurrency,
                           @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal rate,
                           @Size(max = 1000) String note) {

        NewExchange toNewExchange() {
            return new NewExchange(fromCurrency, fromAmount, toCurrency, rate, note);
        }
    }

    record ResolveRequest(@NotBlank @Size(max = 1000) String note) {
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
