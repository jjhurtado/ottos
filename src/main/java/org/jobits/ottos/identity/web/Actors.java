package org.jobits.ottos.identity.web;

import org.jobits.ottos.identity.management.Actor;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class Actors {

    private Actors() {
    }

    static Actor from(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        return new Actor(UUID.fromString(jwt.getSubject()), permissions == null ? Set.of() : Set.copyOf(permissions));
    }
}
