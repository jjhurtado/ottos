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
    void anOldTicketBecomesADeliveredRemittanceOnItsDateAndTheBusinessCashRecordsItsPayment() throws Exception {
        importRows(true, TICKET).andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].remittanceId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remittances", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_movements", Integer.class)).isZero();

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
        // The business was paid then; no courier's cash moved
        mvc.perform(get("/api/v1/cash/remittances/{id}/movements", id).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("REMITTANCE_PAYMENT"))
                .andExpect(jsonPath("$[0].amount").value(220.00))
                .andExpect(jsonPath("$[0].occurredAt").value("2024-06-03T19:30:47Z"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_accounts WHERE type = 'COURIER'", Integer.class))
                .isZero();
        // It shows in its old month and never in Sales' completed feed
        mvc.perform(get("/api/v1/reports/monthly").param("month", "2024-06").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.remittances.total").value(1))
                .andExpect(jsonPath("$.collected").value(220.00));
        assertThat(jdbc.queryForObject("SELECT completion_recorded_at FROM remittances", Instant.class)).isNull();
        mvc.perform(get("/api/v1/audit").param("action", "HISTORICAL_REMITTANCES_IMPORTED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].details.charged").value(220.00));

        // Importing the same ticket again changes nothing
        importRows(false, TICKET).andExpect(jsonPath("$.results[0].status").value("EXISTING"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_movements", Integer.class)).isEqualTo(1);
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
