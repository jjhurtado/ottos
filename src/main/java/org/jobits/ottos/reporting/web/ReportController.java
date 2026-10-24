package org.jobits.ottos.reporting.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.reporting.ReportService;
import org.jobits.ottos.reporting.ReportService.MonthlyRemittances;
import org.jobits.ottos.reporting.ReportService.MonthlyReport;
import org.jobits.ottos.reporting.ReportService.Statistics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
        return reports.monthly(month(month));
    }

    @GetMapping("/monthly/remittances")
    @PreAuthorize("hasAuthority('reports:read')")
    @Operation(summary = "Detail of the monthly report: every remittance registered that month, voided ones included, "
            + "without personal data")
    MonthlyRemittances monthlyRemittances(@RequestParam(required = false) String month) {
        return reports.monthlyRemittances(month(month));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('reports:read')")
    @Operation(summary = "Sales statistics of the remittances registered from one day to another (YYYY-MM-DD, both "
            + "included, Cuba time, at most 366 days): by week, month, day of the week and municipality")
    Statistics statistics(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return reports.statistics(day("from", from), day("to", to));
    }

    private static YearMonth month(String month) {
        try {
            return YearMonth.parse(month == null ? "" : month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "month is required, as YYYY-MM");
        }
    }

    private static LocalDate day(String name, String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", name + " is required, as YYYY-MM-DD");
        }
    }
}
