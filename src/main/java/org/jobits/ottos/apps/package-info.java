/**
 * Mobile app versions: which build of the app each platform should run, so clients know when to update and the API
 * can turn away builds below the minimum. Releases are rows of app_releases inserted by migrations only (contract:
 * docs/contracts/app-version.md).
 * <p>
 * Public API: {@link org.jobits.ottos.apps.AppVersionService}.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Apps")
package org.jobits.ottos.apps;
