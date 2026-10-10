package org.jobits.ottos.identity.security;

import org.jobits.ottos.ApiException;
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

    AuthenticationServiceImpl(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
    }

    @Override
    @Transactional
    public Session login(String email, String password) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        boolean valid = found
                .filter(User::isActive)
                .map(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElse(false);
        if (!valid) {
            throw invalidCredentials();
        }
        return openSession(found.get());
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Session refresh(String refreshToken) {
        RefreshToken token = refreshTokens.find(refreshToken).orElseThrow(AuthenticationServiceImpl::invalidRefreshToken);
        Instant now = Instant.now();
        if (token.isRevoked()) {
            refreshTokens.revokeAll(token.getUserId());
            throw invalidRefreshToken();
        }
        if (token.isExpired(now)) {
            throw invalidRefreshToken();
        }
        token.revoke(now);
        User user = users.findWithRolesById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(AuthenticationServiceImpl::invalidRefreshToken);
        return openSession(user);
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
    }

    private Session openSession(User user) {
        return new Session(user, tokens.issueAccessToken(user), refreshTokens.issue(user.getId()));
    }

    private static ResponseStatusException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials");
    }

    private static ResponseStatusException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Invalid refresh token");
    }
}
