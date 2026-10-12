package org.jobits.ottos.apps;

/** Which build of the mobile app each platform should run (contract: docs/contracts/app-version.md). */
public interface AppVersionService {

    /** What the app of that platform needs to know: latest build, minimum build and where to download. */
    AppVersionView current(Platform platform);

    /** Builds below this one must update (426 APP_UPDATE_REQUIRED); 0 when no release is mandatory. */
    int minimumBuild(Platform platform);

    /**
     * @param latestBuild   highest published build; 0 when nothing is published for the platform
     * @param latestVersion its version name, e.g. 1.0.0-rc.2; null when nothing is published
     * @param minimumBuild  highest mandatory build; 0 when none
     * @param downloadUrl   where to get the latest build (the APK, or TestFlight); null if not configured
     * @param notes         what is new in the latest build, if anything
     */
    record AppVersionView(Platform platform, int latestBuild, String latestVersion, int minimumBuild,
                          String downloadUrl, String notes) {
    }
}
