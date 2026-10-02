package org.jobits.ottos.remittances.web;

import org.jobits.ottos.remittances.application.Viewer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class Viewers {

    private Viewers() {
    }

    static Viewer from(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        return new Viewer(UUID.fromString(jwt.getSubject()), permissions == null ? Set.of() : Set.copyOf(permissions));
    }
}
