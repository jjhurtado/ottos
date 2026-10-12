-- Mobile app releases (contract: docs/contracts/app-version.md). One row per published build of the app; rows are
-- never edited nor deleted. A new release is a new migration inserting its row: there is no endpoint to publish one,
-- so no credential can announce a fake "mandatory update" to every client. The download link is not stored: the
-- server builds it from a fixed base per platform (ottos.apps.*-download-url).
--
-- What an app sees: the latest build is the highest of its platform; the minimum build is the highest one marked
-- mandatory (0 if none). Below the minimum, the API answers 426 APP_UPDATE_REQUIRED.
--
-- First migration after the 1.0 baseline: from here on V1__baseline.sql is never edited.

CREATE TABLE app_releases (
    id           UUID PRIMARY KEY,
    platform     VARCHAR(10)              NOT NULL,
    -- versionCode (Android) / CFBundleVersion (iOS): grows with every build
    build        INT                      NOT NULL,
    -- what people see, e.g. 1.0.0-rc.2
    version_name VARCHAR(30)              NOT NULL,
    -- clients with a lower build must update
    mandatory    BOOLEAN                  NOT NULL DEFAULT FALSE,
    notes        VARCHAR(1000),
    released_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_releases_platform_build UNIQUE (platform, build),
    CONSTRAINT ck_app_releases_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT ck_app_releases_build CHECK (build > 0)
);
