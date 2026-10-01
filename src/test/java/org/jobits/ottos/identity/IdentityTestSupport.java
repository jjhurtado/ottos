package org.jobits.ottos.identity;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.identity.domain.PermissionRepository;
import org.jobits.ottos.identity.domain.RefreshTokenRepository;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.RoleRepository;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared fixtures: every test starts with only the roles seeded by the migrations and no users. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IdentityTestSupport {

    static final String PASSWORD = "test-password-123";
    private static final Set<String> SEEDED_ROLES = Set.of("ADMIN", "SALES", "DELIVERY");

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    PermissionRepository permissions;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetIdentityData() {
        refreshTokens.deleteAll();
        users.deleteAll();
        roles.findAll().stream().filter(r -> !SEEDED_ROLES.contains(r.getCode())).forEach(roles::delete);
    }

    User createUser(String email, String... roleCodes) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), "User " + email);
        user.replaceRoles(Arrays.stream(roleCodes).map(this::role).toList());
        return users.save(user);
    }

    Role createRole(String code, String... permissionCodes) {
        Role role = new Role(code, "Role " + code, null);
        role.replacePermissions(permissions.findAllById(List.of(permissionCodes)));
        return roles.save(role);
    }

    Role role(String code) {
        return roles.findByCode(code).orElseThrow();
    }

    /** Logs in and returns the raw JSON response (accessToken, refreshToken…). */
    String loginResponse(String email) throws Exception {
        return login(email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    String accessToken(String email) throws Exception {
        return JsonPath.read(loginResponse(email), "$.accessToken");
    }

    ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }
}
