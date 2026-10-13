package org.jobits.ottos.remittances;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/historical-import.md. */
class HistoricalImportTest extends ApiTestSupport {

    private static final String TICKET = """
            {"ref": "chat-20240603-153047", "occurredAt": "2024-06-03T15:30:47", "beneficiaryPhone": "+53 5 2223344",
             "beneficiaryName": "Rosa Martínez", "amountDelivered": 200, "fee": 20}""";

    private String adminToken;
    private String placeholderId;
    private String rosaId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        placeholderId = id(mvc.perform(post("/api/v1/customers").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Cliente histórico sin identificar\", \"phone\": \"0\"}")));
        rosaId = id(mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Rosa Martínez", "phone": "53 5222 3344", "street": "Calle 10",
                         "municipalityCode": "2302"}""")));
    }

    @Test
    void anOldTicketBecomesADeliveredRemittanceOnItsDateWithoutTouchingAnyCash() throws Exception {
        importRows(true, TICKET).andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].remittanceId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remittances.remittances", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments.cash_movements", Integer.class)).isZero();

        String body = importRows(false, TICKET,
                """
                {"ref": "chat-20240604-090000", "occurredAt": "2024-06-04T09:00:00", "beneficiaryPhone": "56733312",
                 "amountDelivered": 50, "fee": 5}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.created").value(1))
                .andExpect(jsonPath("$.summary.rejected").value(1))
                .andExpect(jsonPath("$.results[1].code").value("BENEFICIARY_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.results[0].remittanceId");

        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.customerId").value(placeholderId))
                .andExpect(jsonPath("$.beneficiaryId").value(rosaId))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.fee").value(20.00))
                .andExpect(jsonPath("$.total").value(220.00))
                .andExpect(jsonPath("$.feeOverridden").value(true))
                .andExpect(jsonPath("$.courierId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2024-06-03T19:30:47Z"))
                .andExpect(jsonPath("$.completedAt").value("2024-06-03T19:30:47Z"));
        // Paid abroad and delivered long ago: no cash box moves
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments.cash_movements", Integer.class)).isZero();
        // It shows in its old month and never in Sales' completed feed
        mvc.perform(get("/api/v1/reports/monthly").param("month", "2024-06").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.remittances.total").value(1))
                .andExpect(jsonPath("$.collected").value(220.00));
        assertThat(jdbc.queryForObject("SELECT completion_recorded_at FROM remittances.remittances", Instant.class)).isNull();
        mvc.perform(get("/api/v1/audit").param("action", "HISTORICAL_REMITTANCES_IMPORTED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].details.charged").value(220.00));

        // Importing the same ticket again changes nothing
        importRows(false, TICKET).andExpect(jsonPath("$.results[0].status").value("EXISTING"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remittances.remittances", Integer.class)).isEqualTo(1);
    }

    @Test
    void badRowsAreRejectedAlone() throws Exception {
        importRows(false,
                TICKET.replace("\"fee\": 20", "\"fee\": 300").replace("153047", "000001"),
                TICKET.replace("2024-06-03T15:30:47", "2999-01-01T00:00:00").replace("153047", "000002"),
                TICKET.replace("\"amountDelivered\": 200", "\"amountDelivered\": 0").replace("153047", "000003"))
                .andExpect(jsonPath("$.results[0].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[1].code").value("INVALID_OCCURRED_AT"))
                .andExpect(jsonPath("$.results[2].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.summary.rejected").value(3));
    }

    @Test
    void twoBeneficiariesWithTheSamePhoneAreToldApartByName() throws Exception {
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Pedro Martínez", "phone": "5352223344", "street": "Calle 10",
                         "municipalityCode": "2302"}"""));

        importRows(false, TICKET).andExpect(jsonPath("$.results[0].status").value("CREATED"));
        importRows(false, TICKET.replace("Rosa Martínez", "Otra").replace("153047", "000004"))
                .andExpect(jsonPath("$.results[0].code").value("AMBIGUOUS_BENEFICIARY"));
    }

    @Test
    void anAdminRegistersAnOldCupDeliveryOrPickupByHand() throws Exception {
        // The configured USD-CUP rate is kept only as a reference; the remittance keeps the ticket's own rate
        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/rates").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rate\": 410}"));
        mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s",
                                 "occurredAt": "2025-03-10T10:00:00", "targetCurrency": "CUP", "amount": 100, "fee": 10,
                                 "rate": 300, "amountToDeliver": 30000}""".formatted(placeholderId, rosaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.targetCurrency").value("CUP"))
                .andExpect(jsonPath("$.amountToDeliver").value(30000.00))
                .andExpect(jsonPath("$.rate").value(300))
                .andExpect(jsonPath("$.createdAt").value("2025-03-10T14:00:00Z"));
        mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PICKUP", "customerId": "%s", "beneficiaryId": "%s",
                                 "occurredAt": "2025-03-11T10:00:00", "amount": 2500, "fee": 0}""".formatted(placeholderId, rosaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PICKUP"))
                .andExpect(jsonPath("$.cashAmount").value(2500.00));
        // CUP needs its rate and amount; a pickup is in USD
        mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s",
                                 "occurredAt": "2025-03-10T10:00:00", "targetCurrency": "CUP", "amount": 100, "fee": 10}
                                """.formatted(placeholderId, rosaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments.cash_movements", Integer.class)).isZero();
        mvc.perform(get("/api/v1/audit").param("action", "HISTORICAL_REMITTANCE_CREATED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(2));
        String salesToken = tokenFor("sales@ottos.test", "SALES");
        mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "PICKUP", "customerId": "%s", "beneficiaryId": "%s",
                                 "occurredAt": "2025-03-11T10:00:00", "amount": 2500, "fee": 0}""".formatted(placeholderId, rosaId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPickupWithItsTicketRefIsRegisteredOnlyOnce() throws Exception {
        String body = """
                {"ref": "chat-20261006-210318:pickup", "type": "PICKUP", "customerId": "%s", "beneficiaryId": "%s",
                 "occurredAt": "2026-10-06T21:03:18", "amount": 2500, "fee": 0}""".formatted(placeholderId, rosaId);

        String first = mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/remittances/historical").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));

        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM remittances.remittances", String.class))
                .isEqualTo("historical:chat-20261006-210318:pickup");
        mvc.perform(get("/api/v1/reports/monthly").param("month", "2026-10").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.pickups.collected").value(2500.00))
                .andExpect(jsonPath("$.pickups.fees").value(0));
    }

    @Test
    void onlyAdminsImport() throws Exception {
        String salesToken = tokenFor("sales@ottos.test", "SALES");
        mvc.perform(post("/api/v1/remittances/import").header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": \"" + placeholderId + "\", \"remittances\": []}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions importRows(boolean dryRun, String... rows) throws Exception {
        return mvc.perform(post("/api/v1/remittances/import").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRun\": " + dryRun + ", \"customerId\": \"" + placeholderId + "\", \"remittances\": ["
                        + String.join(",", rows) + "]}"));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
