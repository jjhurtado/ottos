package org.jobits.ottos;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for API tests. Every test starts with only the data seeded by the migrations: no users, no custom roles,
 * no rates beyond the seeded ones. Add each new table to {@link #RESET} in foreign-key order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiTestSupport.TestClockConfig.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "test-password-123";

    private static final List<String> RESET = List.of(
            "DELETE FROM cash_movements",
            "DELETE FROM cash_accounts WHERE account_key NOT IN ('BUSINESS:USD', 'BUSINESS:CUP', 'EXTERNAL:USD', 'EXTERNAL:CUP')",
            "DELETE FROM remittance_events",
            "DELETE FROM remittances",
            "DELETE FROM customer_beneficiaries",
            "DELETE FROM beneficiaries",
            "DELETE FROM customers",
            "DELETE FROM refresh_tokens",
            "DELETE FROM user_roles",
            "DELETE FROM users",
            "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code NOT IN ('ADMIN', 'SALES', 'DELIVERY'))",
            "DELETE FROM roles WHERE code NOT IN ('ADMIN', 'SALES', 'DELIVERY')",
            "DELETE FROM exchange_rates WHERE id <> '00000000-0000-0000-0001-000000000001'",
            "DELETE FROM fee_rules WHERE id NOT IN ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0002-000000000002')");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected RoleRepository roles;

    @Autowired
    protected PermissionRepository permissions;

    @Autowired
    protected RefreshTokenRepository refreshTokens;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabaseAndClock() {
        RESET.forEach(jdbc::update);
        clock.reset();
    }

    protected User createUser(String email, String... roleCodes) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), "User " + email);
        user.replaceRoles(Arrays.stream(roleCodes).map(this::role).toList());
        return users.save(user);
    }

    protected Role createRole(String code, String... permissionCodes) {
        Role role = new Role(code, "Role " + code, null);
        role.replacePermissions(permissions.findAllById(List.of(permissionCodes)));
        return roles.save(role);
    }

    protected Role role(String code) {
        return roles.findByCode(code).orElseThrow();
    }

    /** Logs in and returns the raw JSON response (accessToken, refreshToken…). */
    protected String loginResponse(String email) throws Exception {
        return login(email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected String accessToken(String email) throws Exception {
        return JsonPath.read(loginResponse(email), "$.accessToken");
    }

    /** Creates a user with the given roles and returns an access token for them. */
    protected String tokenFor(String email, String... roleCodes) throws Exception {
        createUser(email, roleCodes);
        return accessToken(email);
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(ZoneId.of("America/Havana"));
        }
    }
}
