package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.ClientType;
import org.jobits.ottos.identity.domain.User;

/**
 * Issues HS256-signed access tokens carrying the user's roles and effective permissions.
 * The user's roles and permissions must be loaded.
 */
public interface TokenService {

    /** The user's roles and permissions must be loaded. Mobile tokens last a whole working day, web ones are short. */
    IssuedToken issueAccessToken(User user, ClientType client);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
