package org.jobits.ottos.apps.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jobits.ottos.apps.Platform;

import java.time.Instant;
import java.util.UUID;

/** One published build of the mobile app. Inserted by migrations; never edited. */
@Entity
@Table(name = "app_releases", schema = "apps")
public class AppRelease {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Column(nullable = false)
    private int build;

    @Column(name = "version_name", nullable = false, length = 30)
    private String versionName;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(length = 1000)
    private String notes;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;

    protected AppRelease() {
        // required by JPA
    }

    public Platform getPlatform() {
        return platform;
    }

    public int getBuild() {
        return build;
    }

    public String getVersionName() {
        return versionName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
