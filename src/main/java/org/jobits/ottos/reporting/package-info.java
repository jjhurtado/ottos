/**
 * Reporting and audit. Today: the audit log, which stores every {@link org.jobits.ottos.AuditEvent} that modules
 * publish (staff users, roles, own password changes) and serves it at GET /audit. KPIs and exports come in Phase 4.
 * <p>
 * Public API: {@link org.jobits.ottos.reporting.AuditLogService}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reporting and audit")
package org.jobits.ottos.reporting;
