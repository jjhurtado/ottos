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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Quotes")
@SecurityRequirement(name = "bearer")
class QuoteController {

    private final QuoteService quotes;

    QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasAuthority('rates:read')")
    @Operation(summary = "Calculate fee, total to charge and amount to deliver with the current configuration")
    QuoteService.Quote quote(@Valid @RequestBody QuoteRequest request) {
        return quotes.quote(request.sourceCurrency(), request.targetCurrency(), request.amount());
    }

    record QuoteRequest(@NotNull @Pattern(regexp = "[A-Z]{3}") String sourceCurrency,
                        @NotNull @Pattern(regexp = "[A-Z]{3}") String targetCurrency,
                        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }
}
