package org.jobits.ottos.reporting;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditTest extends ApiTestSupport {

    private String adminToken;
    private User admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = createUser("admin@ottos.test", "ADMIN");
        adminToken = accessToken("admin@ottos.test");
    }

    @Test
    void everyChangeToAUserIsRecordedWithWhoDidItAndNoPasswords() throws Exception {
        String userId = JsonPath.read(body(mvc.perform(post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "rosa@ottos.test", "name": "Rosa", "password": "secret-password-1",
                         "roleIds": ["%s"]}
                        """.formatted(role("DELIVERY").getId())))), "$.id");
        change("/api/v1/users/" + userId, "{\"name\": \"Rosa Martínez\"}");
        change("/api/v1/users/" + userId + "/roles", "{\"roleIds\": [\"" + role("SALES").getId() + "\"]}");
        mvc.perform(post("/api/v1/users/{id}/deactivate", userId).header("Authorization", bearer(adminToken)));
        mvc.perform(post("/api/v1/users/{id}/activate", userId).header("Authorization", bearer(adminToken)));
        change("/api/v1/users/" + userId + "/password", "{\"password\": \"another-secret-2\"}");

        String log = body(audit("targetType", "USER", "targetId", userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.items[*].action", contains("USER_PASSWORD_RESET", "USER_ACTIVATED",
                        "USER_DEACTIVATED", "USER_ROLES_CHANGED", "USER_RENAMED", "USER_CREATED")))
                .andExpect(jsonPath("$.items[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.items[0].actorName").value("User admin@ottos.test"))
                .andExpect(jsonPath("$.items[0].targetLabel").value("rosa@ottos.test"))
                .andExpect(jsonPath("$.items[3].details.before", contains("DELIVERY")))
                .andExpect(jsonPath("$.items[3].details.after", contains("SALES")))
                .andExpect(jsonPath("$.items[4].details.before").value("Rosa"))
                .andExpect(jsonPath("$.items[4].details.after").value("Rosa Martínez")));
        assertThat(log).doesNotContain("secret-password-1").doesNotContain("another-secret-2");
    }

    @Test
    void everyChangeToARoleIsRecorded() throws Exception {
        String roleId = JsonPath.read(body(mvc.perform(post("/api/v1/roles")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "SUPERVISOR", "name": "Supervisor", "permissions": ["remittances:read"]}
                        """))), "$.id");
        change("/api/v1/roles/" + roleId, "{\"name\": \"Supervisora\", \"description\": \"Turno de noche\"}");
        change("/api/v1/roles/" + roleId + "/permissions", "{\"permissions\": [\"remittances:read\", \"cash:read\"]}");
        mvc.perform(delete("/api/v1/roles/{id}", roleId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        audit("targetType", "ROLE", "targetId", roleId)
                .andExpect(jsonPath("$.items[*].action", contains("ROLE_DELETED", "ROLE_PERMISSIONS_CHANGED",
                        "ROLE_DESCRIBED", "ROLE_CREATED")))
                .andExpect(jsonPath("$.items[1].details.added", contains("cash:read")))
                .andExpect(jsonPath("$.items[1].details.removed", hasSize(0)))
                .andExpect(jsonPath("$.items[2].details.after.name").value("Supervisora"))
                .andExpect(jsonPath("$.items[3].targetLabel").value("SUPERVISOR"));
    }

    @Test
    void changingYourOwnPasswordIsRecordedAsYours() throws Exception {
        User sales = createUser("sales@ottos.test", "SALES");
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(accessToken("sales@ottos.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"brand-new-pass\"}"))
                .andExpect(status().isNoContent());

        audit("actorId", sales.getId().toString(), "action", "PASSWORD_CHANGED")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].targetId").value(sales.getId().toString()));
    }

    @Test
    void aRejectedChangeLeavesNoEntry() throws Exception {
        // The only administrator cannot lose the ADMIN role: the change rolls back, and so does its audit entry
        change("/api/v1/users/" + admin.getId() + "/roles", "{\"roleIds\": [\"" + role("SALES").getId() + "\"]}")
                .andExpect(status().isConflict());

        audit("targetId", admin.getId().toString()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void onlyAdministratorsReadTheAuditLog() throws Exception {
        String salesToken = tokenFor("sales@ottos.test", "SALES");

        mvc.perform(get("/api/v1/audit").header("Authorization", bearer(salesToken)))
                .andExpect(status().isForbidden());
    }

    private ResultActions change(String path, String json) throws Exception {
        return mvc.perform(put(path)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions audit(String... params) throws Exception {
        var request = get("/api/v1/audit").header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
