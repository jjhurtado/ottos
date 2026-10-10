package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.User;

/**
 * Issues HS256-signed access tokens carrying the user's roles and effective permissions.
 * The user's roles and permissions must be loaded.
 */
public interface TokenService {

    IssuedToken issueAccessToken(User user);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
