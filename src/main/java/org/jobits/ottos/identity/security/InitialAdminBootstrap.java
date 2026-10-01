package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.RoleRepository;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the first administrator when none exists yet and both ottos.admin.email and
 * ottos.admin.password are set (env OTTOS_ADMIN_EMAIL / OTTOS_ADMIN_PASSWORD).
 */
@Component
class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    InitialAdminBootstrap(UserRepository users,
                          RoleRepository roles,
                          PasswordEncoder passwordEncoder,
                          @Value("${ottos.admin.email:}") String email,
                          @Value("${ottos.admin.password:}") String password) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRoles_Code(Role.ADMIN)) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No ADMIN user exists and OTTOS_ADMIN_EMAIL / OTTOS_ADMIN_PASSWORD are not set; skipping creation.");
            return;
        }
        Role admin = roles.findByCode(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Role ADMIN missing; check the Flyway migrations"));
        User user = new User(email, passwordEncoder.encode(password), "Administrator");
        user.replaceRoles(List.of(admin));
        users.save(user);
        log.info("Initial administrator created: {}", User.normalizeEmail(email));
    }
}
