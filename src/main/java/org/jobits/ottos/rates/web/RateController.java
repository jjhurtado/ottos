package org.jobits.ottos.rates.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jobits.ottos.rates.QuoteService;
import org.jobits.ottos.rates.domain.FeeType;
import org.jobits.ottos.rates.management.RateAdministrationService;
import org.jobits.ottos.rates.management.RateAdministrationService.CorridorView;
import org.jobits.ottos.rates.management.RateAdministrationService.FeeRuleView;
import org.jobits.ottos.rates.management.RateAdministrationService.RateView;
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
@RequestMapping("/api/v1")
@Tag(name = "Rates and fees")
@SecurityRequirement(name = "bearer")
class RateController {

    private final RateAdministrationService administration;
    private final QuoteService quotes;

    RateController(RateAdministrationService administration, QuoteService quotes) {
        this.administration = administration;
        this.quotes = quotes;
    }

    @GetMapping("/corridors")
    @PreAuthorize("hasAuthority('rates:read')")
    @Operation(summary = "Corridors with the exchange rate and fee rule in force")
    List<CorridorView> corridors() {
        return administration.corridors();
    }

    @GetMapping("/corridors/{code}/rates")
    @PreAuthorize("hasAuthority('rates:read')")
    @Operation(summary = "Exchange-rate history of a corridor, newest first")
    List<RateView> rateHistory(@PathVariable String code) {
        return administration.rateHistory(code);
    }

    @PostMapping("/corridors/{code}/rates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('rates:write')")
    @Operation(summary = "Set a new exchange rate; it applies from now on")
    RateView setRate(@PathVariable String code, @Valid @RequestBody RateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return administration.setRate(code, request.rate(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/corridors/{code}/fee-rules")
    @PreAuthorize("hasAuthority('rates:read')")
    @Operation(summary = "Fee-rule history of a corridor, newest first")
    List<FeeRuleView> feeRuleHistory(@PathVariable String code) {
        return administration.feeRuleHistory(code);
    }

    @PostMapping("/corridors/{code}/fee-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('rates:write')")
    @Operation(summary = "Set a new fee rule; it applies from now on")
    FeeRuleView setFeeRule(@PathVariable String code, @Valid @RequestBody FeeRuleRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return administration.setFeeRule(code, request.type(), request.value(), request.minFee(), request.maxFee(),
                UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasAuthority('rates:read')")
    @Operation(summary = "Calculate fee, total to charge and amount to deliver with the current rate and fee rule")
    QuoteService.Quote quote(@Valid @RequestBody QuoteRequest request) {
        return quotes.quote(request.sourceCurrency(), request.targetCurrency(), request.amount());
    }

    record RateRequest(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal rate) {
    }

    record FeeRuleRequest(@NotNull FeeType type,
                          @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal value,
                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minFee,
                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal maxFee) {
    }

    record QuoteRequest(@NotNull @Pattern(regexp = "[A-Z]{3}") String sourceCurrency,
                        @NotNull @Pattern(regexp = "[A-Z]{3}") String targetCurrency,
                        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }
}
