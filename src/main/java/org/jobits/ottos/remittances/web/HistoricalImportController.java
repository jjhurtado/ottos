package org.jobits.ottos.remittances.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.remittances.HistoricalImportService;
import org.jobits.ottos.remittances.HistoricalImportService.HistoricalRow;
import org.jobits.ottos.remittances.HistoricalImportService.ImportResult;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.RemittanceView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/remittances")
@Tag(name = "Historical import")
@SecurityRequirement(name = "bearer")
class HistoricalImportController {

    static final int MAX_ROWS = 500;

    private final HistoricalImportService imports;

    HistoricalImportController(HistoricalImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('customers:import')")
    @Operation(summary = "Import remittances from the old records, delivered on their date: the business cash records their payment; dryRun=true previews without saving")
    ImportResult importHistorical(@Valid @RequestBody ImportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return imports.importHistorical(Boolean.TRUE.equals(request.dryRun()), request.customerId(), request.remittances(),
                UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/historical")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('remittances:historical')")
    @Operation(summary = "Register one old remittance or pickup by hand, completed on its date, with no courier and moving no cash")
    RemittanceView create(@Valid @RequestBody HistoricalRequest request, @AuthenticationPrincipal Jwt jwt) {
        return imports.create(new HistoricalImportService.NewHistorical(request.type(), request.customerId(),
                request.beneficiaryId(), request.occurredAt(), request.targetCurrency(), request.amount(), request.fee(),
                request.rate(), request.amountToDeliver(), request.notes()), Viewers.from(jwt));
    }

    record HistoricalRequest(@NotNull RemittanceType type, @NotNull UUID customerId, @NotNull UUID beneficiaryId,
                             @NotNull LocalDateTime occurredAt, @Pattern(regexp = "USD|CUP") String targetCurrency,
                             @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amount,
                             @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 2) BigDecimal fee,
                             @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal rate,
                             @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 2) BigDecimal amountToDeliver,
                             @Size(max = 1000) String notes) {
    }

    /** Each row is checked on its own and rejected alone; only the envelope is validated here. */
    record ImportRequest(Boolean dryRun, @NotNull UUID customerId,
                         @NotNull @Size(max = MAX_ROWS) List<@NotNull HistoricalRow> remittances) {
    }
}
