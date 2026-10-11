package org.jobits.ottos.beneficiaries.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.beneficiaries.CustomerTransferService;
import org.jobits.ottos.beneficiaries.CustomerTransferService.BeneficiaryImportResult;
import org.jobits.ottos.beneficiaries.CustomerTransferService.CustomerExport;
import org.jobits.ottos.beneficiaries.CustomerTransferService.ImportBeneficiary;
import org.jobits.ottos.beneficiaries.CustomerTransferService.ImportCustomer;
import org.jobits.ottos.beneficiaries.CustomerTransferService.ImportResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Customer and beneficiary import and export")
@SecurityRequirement(name = "bearer")
class CustomerTransferController {

    static final int MAX_CUSTOMERS = 500;

    private final CustomerTransferService transfer;

    CustomerTransferController(CustomerTransferService transfer) {
        this.transfer = transfer;
    }

    @GetMapping("/customers/export")
    @PreAuthorize("hasAuthority('customers:export')")
    @Operation(summary = "Every customer with their beneficiaries, as JSON; the frontend writes the spreadsheet")
    CustomerExport export(@AuthenticationPrincipal Jwt jwt) {
        return transfer.exportAll(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/customers/import")
    @PreAuthorize("hasAuthority('customers:import')")
    @Operation(summary = "Import customers with their beneficiaries; dryRun=true previews the result without saving")
    ImportResult importCustomers(@Valid @RequestBody ImportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return transfer.importCustomers(Boolean.TRUE.equals(request.dryRun()), request.customers(),
                UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/beneficiaries/import")
    @PreAuthorize("hasAuthority('customers:import')")
    @Operation(summary = "Import beneficiaries without a customer; dryRun=true previews the result without saving")
    BeneficiaryImportResult importBeneficiaries(@Valid @RequestBody BeneficiaryImportRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return transfer.importBeneficiaries(Boolean.TRUE.equals(request.dryRun()), request.beneficiaries(),
                UUID.fromString(jwt.getSubject()));
    }

    record BeneficiaryImportRequest(Boolean dryRun,
                                    @NotNull @Size(max = MAX_CUSTOMERS) List<@NotNull ImportBeneficiary> beneficiaries) {
    }

    /** Each row is checked on its own and rejected alone; only the envelope is validated here. */
    record ImportRequest(Boolean dryRun, @NotNull @Size(max = MAX_CUSTOMERS) List<@NotNull ImportCustomer> customers) {
    }
}
