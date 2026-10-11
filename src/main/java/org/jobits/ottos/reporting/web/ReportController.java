package org.jobits.ottos.reporting.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.reporting.ReportService;
import org.jobits.ottos.reporting.ReportService.MonthlyReport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
@SecurityRequirement(name = "bearer")
class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('reports:read')")
    @Operation(summary = "Monthly sales report (month=YYYY-MM, Cuba time): remittances registered that month, in USD")
    MonthlyReport monthly(@RequestParam(required = false) String month) {
        return reports.monthly(parse(month));
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month == null ? "" : month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "month is required, as YYYY-MM");
        }
    }
}
