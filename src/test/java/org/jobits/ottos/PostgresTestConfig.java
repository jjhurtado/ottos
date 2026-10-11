package org.jobits.ottos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Tests run against a real PostgreSQL, the same version as docker-compose, started by Testcontainers (Docker needed).
 * The Spring test context is cached, so one container serves every API test of a run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
