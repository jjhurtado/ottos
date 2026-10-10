package org.jobits.ottos.remittances.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jobits.ottos.remittances.application.RemittanceStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Statistics live here because they are computed from remittances; the history itself is GET /remittances?customerId=. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Statistics")
@SecurityRequirement(name = "bearer")
class RemittanceStatsController {

    private final RemittanceStatsService stats;

    RemittanceStatsController(RemittanceStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/customers/{id}/stats")
    @PreAuthorize("hasAuthority('remittances:read')")
    @Operation(summary = "Customer statistics: totals, last 12 months, top beneficiaries and municipalities")
    RemittanceStatsService.CustomerStats customer(@PathVariable UUID id) {
        return stats.forCustomer(id);
    }

    @GetMapping("/beneficiaries/{id}/stats")
    @PreAuthorize("hasAuthority('remittances:read')")
    @Operation(summary = "Beneficiary statistics: received by currency, pickups and top senders")
    RemittanceStatsService.BeneficiaryStats beneficiary(@PathVariable UUID id) {
        return stats.forBeneficiary(id);
    }
}
