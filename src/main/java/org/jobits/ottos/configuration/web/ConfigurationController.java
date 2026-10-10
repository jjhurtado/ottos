package org.jobits.ottos.configuration.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ConfigurationService.ConfigurationView;
import org.jobits.ottos.configuration.ConfigurationService.CorridorView;
import org.jobits.ottos.configuration.ConfigurationService.FeeRuleView;
import org.jobits.ottos.configuration.ConfigurationService.RateView;
import org.jobits.ottos.configuration.ConfigurationService.SettingsView;
import org.jobits.ottos.configuration.FeeType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/configuration")
@Tag(name = "Configuration")
@SecurityRequirement(name = "bearer")
class ConfigurationController {

    private final ConfigurationService configuration;

    ConfigurationController(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('configuration:read')")
    @Operation(summary = "All the configuration at once: settings and corridors with the rate and fee rule in force")
    ConfigurationView get() {
        return configuration.get();
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('configuration:read')")
    @Operation(summary = "Minimum amount per remittance (USD; 0 = no minimum) and default delivery days")
    SettingsView settings() {
        return configuration.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('configuration:write')")
    @Operation(summary = "Change the settings; fields left out keep their value")
    SettingsView updateSettings(@Valid @RequestBody SettingsRequest request, @AuthenticationPrincipal Jwt jwt) {
        return configuration.updateSettings(request.minimumAmount(), request.defaultDeliveryDays(),
                UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/corridors")
    @PreAuthorize("hasAuthority('configuration:read')")
    @Operation(summary = "Corridors with the exchange rate and fee rule in force")
    List<CorridorView> corridors() {
        return configuration.corridors();
    }

    @GetMapping("/corridors/{code}/rates")
    @PreAuthorize("hasAuthority('configuration:read')")
    @Operation(summary = "Exchange-rate history of a corridor, newest first")
    List<RateView> rateHistory(@PathVariable String code) {
        return configuration.rateHistory(code);
    }

    @PostMapping("/corridors/{code}/rates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    @Operation(summary = "Set a new exchange rate; it applies from now on")
    RateView setRate(@PathVariable String code, @Valid @RequestBody RateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return configuration.setRate(code, request.rate(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/corridors/{code}/fee-rules")
    @PreAuthorize("hasAuthority('configuration:read')")
    @Operation(summary = "Fee-rule history of a corridor, newest first")
    List<FeeRuleView> feeRuleHistory(@PathVariable String code) {
        return configuration.feeRuleHistory(code);
    }

    @PostMapping("/corridors/{code}/fee-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('configuration:write')")
    @Operation(summary = "Set a new fee rule; it applies from now on")
    FeeRuleView setFeeRule(@PathVariable String code, @Valid @RequestBody FeeRuleRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return configuration.setFeeRule(code, request.type(), request.value(), request.minFee(), request.maxFee(),
                UUID.fromString(jwt.getSubject()));
    }

    record SettingsRequest(@DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal minimumAmount,
                           @Min(1) @Max(60) Integer defaultDeliveryDays) {
    }

    record RateRequest(@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal rate) {
    }

    record FeeRuleRequest(@NotNull FeeType type,
                          @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal value,
                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minFee,
                          @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal maxFee) {
    }
}
