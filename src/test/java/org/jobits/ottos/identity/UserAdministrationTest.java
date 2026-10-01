package org.jobits.ottos.identity;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdministrationTest extends IdentityTestSupport {

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        admin = createUser("admin@ottos.test", "ADMIN");
        adminToken = accessToken("admin@ottos.test");
    }

    @Test
    void adminCreatesAUserWhoCanLogIn() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "New.Agent@ottos.test", "name": "New agent", "password": "agent-pass-123",
                                 "roleIds": ["%s"]}
                                """.formatted(role("SALES").getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.agent@ottos.test"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.roles[0].code").value("SALES"));

        login("new.agent@ottos.test", "agent-pass-123").andExpect(status().isOk());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ADMIN@ottos.test", "name": "Copy", "password": "agent-pass-123", "roleIds": []}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void userWithoutPermissionGets403() throws Exception {
        createUser("sales@ottos.test", "SALES");

        mvc.perform(get("/api/v1/users").header("Authorization", bearer(accessToken("sales@ottos.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsUsersWithTheirRoles() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@ottos.test"))
                .andExpect(jsonPath("$[0].roles[0].code").value("ADMIN"));
    }

    @Test
    void cannotDeactivateYourself() throws Exception {
        mvc.perform(post("/api/v1/users/{id}/deactivate", admin.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void lastActiveAdminCannotLoseTheAdminRole() throws Exception {
        mvc.perform(put("/api/v1/users/{id}/roles", admin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": []}"))
                .andExpect(status().isConflict());

        createUser("second.admin@ottos.test", "ADMIN");
        mvc.perform(put("/api/v1/users/{id}/roles", admin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void deactivatingAUserEndsTheirSessions() throws Exception {
        User sales = createUser("sales@ottos.test", "SALES");
        String refreshToken = JsonPath.read(loginResponse("sales@ottos.test"), "$.refreshToken");

        mvc.perform(post("/api/v1/users/{id}/deactivate", sales.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        refresh(refreshToken).andExpect(status().isUnauthorized());
        login("sales@ottos.test", PASSWORD).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/users/{id}/activate", sales.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        login("sales@ottos.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void cannotHandOutPermissionsYouDoNotHold() throws Exception {
        createRole("USER_MANAGER", "users:read", "users:write");
        createUser("manager@ottos.test", "USER_MANAGER");
        User sales = createUser("sales@ottos.test", "SALES");
        String managerToken = accessToken("manager@ottos.test");

        mvc.perform(put("/api/v1/users/{id}/roles", sales.getId())
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [\"%s\"]}".formatted(role("ADMIN").getId())))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/users/{id}/roles", sales.getId())
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [\"%s\"]}".formatted(role("DELIVERY").getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].code").value("DELIVERY"));
    }

    @Test
    void adminResetsAPassword() throws Exception {
        User sales = createUser("sales@ottos.test", "SALES");

        mvc.perform(put("/api/v1/users/{id}/password", sales.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"reset-pass-789\"}"))
                .andExpect(status().isNoContent());

        login("sales@ottos.test", "reset-pass-789").andExpect(status().isOk());
    }
}
