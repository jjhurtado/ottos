package org.jobits.ottos.apps.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppReleaseRepository extends JpaRepository<AppRelease, UUID> {
}
