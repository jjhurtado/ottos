package org.jobits.ottos.identity;

import org.jobits.ottos.ApiTestSupport;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.identity.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAdministrationTest extends ApiTestSupport {

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("admin@ottos.test", "ADMIN");
        adminToken = accessToken("admin@ottos.test");
    }

    @Test
    void listsThePermissionCatalog() throws Exception {
        mvc.perform(get("/api/v1/permissions").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("users:read")))
                .andExpect(jsonPath("$[0].module").value("identity"));
    }

    @Test
    void aNewRoleGrantsExactlyItsPermissions() throws Exception {
        String body = mvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "SUPERVISOR", "name": "Branch supervisor", "permissions": ["users:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.permissions[0]").value("users:read"))
                .andReturn().getResponse().getContentAsString();
        String roleId = JsonPath.read(body, "$.id");

        String userBody = mvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "supervisor@ottos.test", "name": "Supervisor", "password": "%s", "roleIds": ["%s"]}
                                """.formatted(PASSWORD, roleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String supervisorToken = accessToken("supervisor@ottos.test");
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(supervisorToken))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/roles").header("Authorization", bearer(supervisorToken))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/{id}/deactivate", JsonPath.<String>read(userBody, "$.id"))
                        .header("Authorization", bearer(supervisorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateCodeReturns409() throws Exception {
        mvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"SALES\", \"name\": \"Copy\", \"permissions\": []}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidCodeReturns400() throws Exception {
        mvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"branch supervisor\", \"name\": \"Supervisor\", \"permissions\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPermissionReturns400() throws Exception {
        mvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"AUDITOR\", \"name\": \"Auditor\", \"permissions\": [\"does:not-exist\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionsAndDescriptionCanBeChanged() throws Exception {
        Role auditor = createRole("AUDITOR", "users:read");

        mvc.perform(put("/api/v1/roles/{id}/permissions", auditor.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": [\"roles:read\", \"users:read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("roles:read"))
                .andExpect(jsonPath("$.permissions[1]").value("users:read"));

        mvc.perform(put("/api/v1/roles/{id}", auditor.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Internal auditor\", \"description\": \"Read-only access\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Internal auditor"));
    }

    @Test
    void builtInRoleIsReadOnly() throws Exception {
        Role admin = role("ADMIN");

        mvc.perform(put("/api/v1/roles/{id}/permissions", admin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": []}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/roles/{id}", admin.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void roleInUseCannotBeDeleted() throws Exception {
        Role auditor = createRole("AUDITOR");
        createUser("auditor@ottos.test", "AUDITOR");
        Role unused = createRole("UNUSED");

        mvc.perform(delete("/api/v1/roles/{id}", auditor.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/roles/{id}", unused.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotCreateARoleWithPermissionsYouDoNotHold() throws Exception {
        createRole("ROLE_MANAGER", "roles:read", "roles:write");
        createUser("manager@ottos.test", "ROLE_MANAGER");

        mvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer(accessToken("manager@ottos.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"POWER\", \"name\": \"Power\", \"permissions\": [\"users:write\"]}"))
                .andExpect(status().isForbidden());
    }
}
