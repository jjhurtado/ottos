package org.jobits.ottos.identity;

import org.jobits.ottos.ApiTestSupport;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.identity.ClientType;
import org.jobits.ottos.identity.domain.RefreshToken;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationTest extends ApiTestSupport {

    @BeforeEach
    void setUp() {
        createUser("Sales@Ottos.test", "SALES");
        createUser("admin@ottos.test", "ADMIN");
        User inactive = new User("inactive@ottos.test", passwordEncoder.encode(PASSWORD), "Inactive user");
        inactive.deactivate();
        users.save(inactive);
    }

    @Test
    void cleanupDeletesOnlyExpiredRefreshTokens(@Autowired RefreshTokenService refreshTokenService) {
        User sales = users.findByEmail("sales@ottos.test").orElseThrow();
        Instant now = Instant.now();
        refreshTokens.save(new RefreshToken(sales.getId(), ClientType.WEB, "expired", now.minus(Duration.ofDays(31)),
                now.minus(Duration.ofDays(1))));
        refreshTokens.save(new RefreshToken(sales.getId(), ClientType.WEB, "valid", now, now.plus(Duration.ofDays(30))));
        RefreshToken revoked = new RefreshToken(sales.getId(), ClientType.WEB, "revoked", now,
                now.plus(Duration.ofDays(30)));
        revoked.revoke(now);
        refreshTokens.save(revoked);

        assertThat(refreshTokenService.deleteExpired()).isEqualTo(1);

        // Revoked but not expired is kept: presenting it again must still be detected as reuse
        assertThat(refreshTokens.findByTokenHash("expired")).isEmpty();
        assertThat(refreshTokens.findByTokenHash("valid")).isPresent();
        assertThat(refreshTokens.findByTokenHash("revoked")).isPresent();
    }

    @Test
    void validLoginReturnsTokenPair() throws Exception {
        login("sales@ottos.test", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").isNumber());
    }

    @Test
    void meShowsRolesAndEffectivePermissions() throws Exception {
        String token = accessToken("admin@ottos.test");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ottos.test"))
                .andExpect(jsonPath("$.type").value("STAFF"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions", hasItem("users:read")))
                .andExpect(jsonPath("$.permissions", hasItem("roles:write")));
    }

    @Test
    void noTokenMeansNoAccess() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = accessToken("sales@ottos.test");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token + "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        login("sales@ottos.test", "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        login("inactive@ottos.test", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedEmailReturns400() throws Exception {
        login("not-an-email", PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void refreshIssuesNewPairAndConsumesTheOldToken() throws Exception {
        String first = JsonPath.read(loginResponse("sales@ottos.test"), "$.refreshToken");

        String body = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$.refreshToken");
        usedTokensAreOlderThanTheGracePeriod();

        refresh(first).andExpect(status().isUnauthorized());
        // Reusing a consumed token means it leaked: every session of that user is revoked.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshPicksUpPermissionChanges() throws Exception {
        Role auditor = createRole("AUDITOR");
        createUser("auditor@ottos.test", "AUDITOR");
        String login = loginResponse("auditor@ottos.test");
        String access = JsonPath.read(login, "$.accessToken");
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(access))).andExpect(status().isForbidden());

        auditor.replacePermissions(permissions.findAllById(List.of("users:read")));
        roles.save(auditor);

        String refreshed = refresh(JsonPath.read(login, "$.refreshToken"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(JsonPath.read(refreshed, "$.accessToken"))))
                .andExpect(status().isOk());
    }

    @Test
    void aRetryWithinTheGracePeriodGetsANewPairInsteadOfSigningOut() throws Exception {
        String first = JsonPath.read(loginResponse("sales@ottos.test"), "$.refreshToken");
        String lost = JsonPath.read(refresh(first).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        // The first response never arrived: the app retries with the same token
        String retried = JsonPath.read(refresh(first)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refresh(retried).andExpect(status().isOk());
        // The pair that never arrived was revoked; presenting it now is reuse, as before
        refresh(lost).andExpect(status().isUnauthorized());
    }

    @Test
    void theGracePeriodDoesNotApplyAfterLogout() throws Exception {
        String first = JsonPath.read(loginResponse("sales@ottos.test"), "$.refreshToken");
        String second = JsonPath.read(refresh(first).andReturn().getResponse().getContentAsString(), "$.refreshToken");
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + second + "\"}"))
                .andExpect(status().isNoContent());

        refresh(first).andExpect(status().isUnauthorized());
    }

    @Test
    void mobileSessionsLastAWorkingDayAndKeepTheirClientOnRefresh() throws Exception {
        String web = login("sales@ottos.test", PASSWORD).andExpect(jsonPath("$.expiresIn").value(300))
                .andReturn().getResponse().getContentAsString();
        String mobile = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"sales@ottos.test\", \"password\": \"" + PASSWORD + "\", \"client\": \"MOBILE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(14 * 3600))
                .andReturn().getResponse().getContentAsString();

        refresh(JsonPath.read(mobile, "$.refreshToken")).andExpect(jsonPath("$.expiresIn").value(14 * 3600));
        refresh(JsonPath.read(web, "$.refreshToken")).andExpect(jsonPath("$.expiresIn").value(300));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(JsonPath.read(mobile, "$.accessToken"))))
                .andExpect(jsonPath("$.client").value("MOBILE"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForAWhile() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("sales@ottos.test", "wrong-password").andExpect(status().isUnauthorized());
        }

        // Locked: even the right password is refused until the lock ends
        login("sales@ottos.test", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(header().string("Retry-After", "900"));
        login("admin@ottos.test", PASSWORD).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(15));
        login("sales@ottos.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginClearsTheFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("sales@ottos.test", "wrong-password");
        }
        login("sales@ottos.test", PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("sales@ottos.test", "wrong-password");
        }

        login("sales@ottos.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anUnknownEmailIsLockedLikeAnyOtherSoItRevealsNothing() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("nobody@ottos.test", "whatever").andExpect(status().isUnauthorized());
        }

        login("nobody@ottos.test", "whatever")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void manyFailuresFromOneIpOnDifferentEmailsLockTheIp() throws Exception {
        for (int i = 0; i < 50; i++) {
            login("guess" + i + "@ottos.test", "whatever").andExpect(status().isUnauthorized());
        }

        login("sales@ottos.test", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = JsonPath.read(loginResponse("sales@ottos.test"), "$.refreshToken");

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenReturns401() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresTheCurrentOneAndEndsOtherSessions() throws Exception {
        String login = loginResponse("sales@ottos.test");
        String access = JsonPath.read(login, "$.accessToken");

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"wrong-password\", \"newPassword\": \"new-password-456\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"new-password-456\"}"))
                .andExpect(status().isNoContent());

        refresh(JsonPath.read(login, "$.refreshToken")).andExpect(status().isUnauthorized());
        login("sales@ottos.test", PASSWORD).andExpect(status().isUnauthorized());
        login("sales@ottos.test", "new-password-456").andExpect(status().isOk());
    }

    /** Moves the use of every consumed refresh token back beyond ottos.security.refresh-reuse-grace. */
    private void usedTokensAreOlderThanTheGracePeriod() {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = ? WHERE revoked_at IS NOT NULL",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))));
    }
}
