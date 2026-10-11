package org.jobits.ottos.reporting.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jobits.ottos.reporting.AuditLogService;
import org.jobits.ottos.reporting.AuditLogService.AuditFilter;
import org.jobits.ottos.reporting.AuditLogService.AuditPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
@SecurityRequirement(name = "bearer")
@Validated
class AuditController {

    private final AuditLogService audit;

    AuditController(AuditLogService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Audit log, newest first: who changed what and when; filter by target, actor, action and dates")
    AuditPage search(@RequestParam(required = false) String targetType,
                     @RequestParam(required = false) String targetId,
                     @RequestParam(required = false) UUID actorId,
                     @RequestParam(required = false) String action,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(defaultValue = "0") @Min(0) int page,
                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return audit.search(new AuditFilter(targetType, targetId, actorId, action, from, to), page, size);
    }
}
