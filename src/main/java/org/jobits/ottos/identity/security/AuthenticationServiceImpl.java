package org.jobits.ottos.identity.security;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.domain.ClientType;
import org.jobits.ottos.identity.domain.RefreshToken;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Implementation of {@link AuthenticationService}. */
@Service
class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final JwtProperties props;
    private final StaffAudit audit;
    private final LoginAttempts attempts;

    AuthenticationServiceImpl(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                              RefreshTokenService refreshTokens, JwtProperties props, StaffAudit audit,
                              LoginAttempts attempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.audit = audit;
        this.attempts = attempts;
    }

    @Override
    @Transactional
    public Session login(String email, String password, ClientType client, String ip) {
        String normalized = User.normalizeEmail(email);
        attempts.check(normalized, ip);
        Optional<User> found = users.findByEmail(normalized);
        boolean valid = found
                .filter(User::isActive)
                .map(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElse(false);
        if (!valid) {
            attempts.failed(normalized, ip);
            throw invalidCredentials();
        }
        attempts.succeeded(normalized);
        return openSession(found.get(), client);
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Session refresh(String refreshToken) {
        RefreshToken token = refreshTokens.find(refreshToken).orElseThrow(AuthenticationServiceImpl::invalidRefreshToken);
        Instant now = Instant.now();
        if (token.isRevoked()) {
            Optional<RefreshToken> unreceived = unreceivedSuccessor(token, now);
            if (unreceived.isEmpty()) {
                refreshTokens.revokeAll(token.getUserId());
                throw invalidRefreshToken();
            }
            unreceived.get().revoke(now);
        }
        if (token.isExpired(now)) {
            throw invalidRefreshToken();
        }
        User user = users.findWithRolesById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(AuthenticationServiceImpl::invalidRefreshToken);
        Session session = openSession(user, token.getClient());
        token.replaceWith(session.refreshToken().id(), now);
        return session;
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.find(refreshToken).ifPresent(t -> t.revoke(Instant.now()));
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("AUTHENTICATION_REQUIRED", "Your account is no longer active"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INCORRECT_CURRENT_PASSWORD", "Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(userId);
        audit.passwordChanged(user);
    }

    private Session openSession(User user, ClientType client) {
        return new Session(user, tokens.issueAccessToken(user, client), refreshTokens.issue(user.getId(), client));
    }

    /**
     * The token issued when this one was used, if this looks like a retry: it was used within the grace period and
     * what it issued has not been used, revoked or expired since (so the client never got it).
     */
    private Optional<RefreshToken> unreceivedSuccessor(RefreshToken token, Instant now) {
        if (!token.isRecentlyReplaced(now, props.refreshReuseGrace())) {
            return Optional.empty();
        }
        return refreshTokens.findById(token.getReplacedBy())
                .filter(successor -> !successor.isRevoked() && !successor.isExpired(now));
    }

    private static ResponseStatusException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
    }

    private static ResponseStatusException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token");
    }
}
