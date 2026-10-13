package org.jobits.ottos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Each module's tables live in its own schema; public only keeps Flyway's history (STANDARDS.md). */
class SchemaLayoutTest extends ApiTestSupport {

    @Test
    void publicOnlyHoldsFlywaysHistory() {
        List<String> inPublic = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(inPublic).containsExactly("flyway_schema_history");
    }

    @Test
    void everyModuleHasItsSchema() {
        List<String> schemas = jdbc.queryForList("""
                SELECT DISTINCT table_schema FROM information_schema.tables
                WHERE table_schema NOT IN ('public', 'pg_catalog', 'information_schema') ORDER BY 1""", String.class);

        assertThat(schemas).containsExactly("apps", "beneficiaries", "branches", "configuration", "customers",
                "identity", "payments", "remittances", "reporting");
    }
}
