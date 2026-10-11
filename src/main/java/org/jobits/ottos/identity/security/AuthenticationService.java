package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.ClientType;
import org.jobits.ottos.identity.domain.User;

import java.util.UUID;

public interface AuthenticationService {

    /**
     * Checks the credentials and issues a token pair. Every failure returns the same 401,
     * so the response never reveals whether the email exists.
     */
    Session login(String email, String password, ClientType client);

    /**
     * Exchanges a refresh token for a new pair of the same client, reloading the user's current roles and permissions.
     * The presented token is consumed; presenting an already used token revokes every session of that user, since it
     * means the token was copied. Exception: within ottos.security.refresh-reuse-grace of its use, a retry (the first
     * response was lost) revokes the pair issued then, which never arrived, and gets a new one.
     */
    Session refresh(String refreshToken);

    /** Revokes the given refresh token. Unknown or already revoked tokens are ignored. */
    void logout(String refreshToken);

    /** Changes the caller's own password and signs them out of every device. */
    void changePassword(UUID userId, String currentPassword, String newPassword);

    record Session(User user, TokenService.IssuedToken accessToken,
                   RefreshTokenService.IssuedRefreshToken refreshToken) {
    }
}
