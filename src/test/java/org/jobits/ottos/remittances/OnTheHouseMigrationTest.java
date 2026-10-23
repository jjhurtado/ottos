package org.jobits.ottos.remittances;

import org.flywaydb.core.Flyway;
import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3 (on the house) applied on a database already at V2 with remittances in it, as staging and production are:
 * the existing ones stay as they were, not on the house, and the new CHECKs hold.
 */
class OnTheHouseMigrationTest extends ApiTestSupport {

    private static final String DATABASE = "migration_v3";

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void v3KeepsTheExistingRemittancesAsTheyWere() throws Exception {
        jdbc.execute("DROP DATABASE IF EXISTS " + DATABASE);
        jdbc.execute("CREATE DATABASE " + DATABASE);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + DATABASE);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());
        JdbcTemplate db = new JdbcTemplate(dataSource);
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("2").load();
        flyway.migrate();
        db.execute(Files.readString(Path.of("scripts/data/seed/cuba-la-habana.sql")));
        db.update("""
                INSERT INTO customers.customers (id, full_name, phone, created_at)
                VALUES ('10000000-0000-0000-0000-000000000001', 'Ana Pérez', '+13055550101', now())""");
        db.update("""
                INSERT INTO beneficiaries.beneficiaries (id, full_name, phone, street, municipality_code, created_at)
                VALUES ('20000000-0000-0000-0000-000000000001', 'Rosa Martínez', '5352223344', 'Calle 10', '2302', now())""");
        insertRemittance(db, "OT-AAAAAA", "DELIVERY", "100.00", "10.00", "110.00");
        insertRemittance(db, "OT-BBBBBB", "PICKUP", "200.00", "0.00", "200.00");

        flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        flyway.migrate();

        List<Map<String, Object>> rows = db.queryForList("""
                SELECT code, fee, total, on_the_house, waived_fee, on_the_house_reason
                FROM remittances.remittances ORDER BY code""");
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("on_the_house")).isEqualTo(false);
            assertThat(row.get("waived_fee")).isNull();
            assertThat(row.get("on_the_house_reason")).isNull();
        });
        assertThat(rows.get(0).get("fee").toString()).isEqualTo("10.00");
        assertThat(rows.get(0).get("total").toString()).isEqualTo("110.00");
        assertThat(db.queryForList("""
                SELECT r.code FROM identity.role_permissions rp JOIN identity.roles r ON r.id = rp.role_id
                WHERE rp.permission_code = 'remittances:on-the-house'""", String.class)).containsExactly("ADMIN");

        // The CHECKs: only a delivery with fee 0, total = amount, a waived fee and a reason
        assertThatThrownBy(() -> db.update("""
                UPDATE remittances.remittances SET on_the_house = TRUE, waived_fee = 10, on_the_house_reason = 'x'
                WHERE code = 'OT-AAAAAA'""")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("""
                UPDATE remittances.remittances SET on_the_house = TRUE, waived_fee = 20, on_the_house_reason = 'x'
                WHERE code = 'OT-BBBBBB'""")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("""
                UPDATE remittances.remittances SET fee = 0, total = amount, on_the_house = TRUE, waived_fee = 10,
                    on_the_house_reason = '  ' WHERE code = 'OT-AAAAAA'""")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("""
                UPDATE remittances.remittances SET fee = 0, total = amount, on_the_house = TRUE,
                    on_the_house_reason = 'Familia' WHERE code = 'OT-AAAAAA'""")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.update("""
                UPDATE remittances.remittances SET fee = 0, total = amount, on_the_house = TRUE, waived_fee = 10,
                    on_the_house_reason = 'Familia' WHERE code = 'OT-AAAAAA'""")).isEqualTo(1);

        jdbc.execute("DROP DATABASE " + DATABASE + " WITH (FORCE)");
    }

    private static void insertRemittance(JdbcTemplate db, String code, String type, String amount, String fee,
                                         String total) {
        db.update("""
                INSERT INTO remittances.remittances (id, code, type, status, customer_id, beneficiary_id, beneficiary_name,
                    beneficiary_phone, beneficiary_address, beneficiary_municipality_code, corridor_code, source_currency,
                    target_currency, amount, fee, total, rate, amount_to_deliver, exchange_rate_id, fee_rule_id, pin,
                    expected_date, created_at, created_by, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 'PAID', '10000000-0000-0000-0000-000000000001',
                    '20000000-0000-0000-0000-000000000001', 'Rosa Martínez', '5352223344', 'Calle 10', '2302', 'USD-USD',
                    'USD', 'USD', ?::numeric, ?::numeric, ?::numeric, 1, ?::numeric,
                    '00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0002-000000000002', '123456',
                    current_date, now(), gen_random_uuid(), now())""", code, type, amount, fee, total, amount);
    }
}
