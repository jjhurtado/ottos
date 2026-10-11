/**
 * Reporting and audit: the audit log, which stores every {@link org.jobits.ottos.AuditEvent} that modules publish
 * (staff users, roles, own password changes) and serves it at GET /audit; and the business reports, today the monthly
 * sales report (GET /reports/monthly), whose figures come from the remittances module.
 * <p>
 * Public API: {@link org.jobits.ottos.reporting.AuditLogService}, {@link org.jobits.ottos.reporting.ReportService}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reporting and audit")
package org.jobits.ottos.reporting;
