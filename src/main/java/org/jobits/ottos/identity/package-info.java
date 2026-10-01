/**
 * Identity and access for staff: login, JWT access tokens and rotating refresh tokens, staff users,
 * and dynamic roles and permissions (RBAC) managed through the API.
 * <p>
 * Other modules protect their endpoints with permission codes, e.g.
 * {@code @PreAuthorize("hasAuthority('remittances:create')")}, and add those codes to the catalog with a
 * Flyway migration that also grants them to ADMIN. Everything in sub-packages is internal.
 * Phase 0.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity and access")
package org.jobits.ottos.identity;
