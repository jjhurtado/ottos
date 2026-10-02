package org.jobits.ottos.identity;

import org.jobits.ottos.ApiTestSupport;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
