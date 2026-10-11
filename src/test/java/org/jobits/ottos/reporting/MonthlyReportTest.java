package org.jobits.ottos.reporting;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/monthly-report.md. */
class MonthlyReportTest extends ApiTestSupport {

    private String adminToken;
    private String salesToken;
    private String courierToken;
    private User courier;
    private String customerId;
    private String beneficiaryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
        courier = createUser("courier@ottos.test", "DELIVERY");
        courierToken = accessToken("courier@ottos.test");
        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/rates")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\": 410}"));
        customerId = id(mvc.perform(post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Ana Pérez\", \"phone\": \"+13055550101\"}")));
        beneficiaryId = id(mvc.perform(post("/api/v1/customers/{c}/beneficiaries", customerId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Rosa Martínez", "phone": "5352223344", "street": "Calle 10", "municipalityCode": "2302"}
                        """)));
    }

    @Test
    void theMonthAddsUpWhatWasRegisteredInIt() throws Exception {
        // 100 USD → 41 000 CUP (100.00 USD at 410); fee 10; collected 110
        String delivered = register("DELIVERY", "100", "CUP");
        // 37 USD → 15 170 CUP rounded down to 15 150 (36.95 USD at 410); fee 10 (minimum); collected 47
        String withIncident = register("DELIVERY", "37", "CUP");
        // 100 USD → 100 USD; fee 10; collected 110
        register("DELIVERY", "100", "USD");
        // pickup of 200 USD: the courier collects 200 USD, nothing is delivered; fee 20 is income only
        register("PICKUP", "200", "CUP");
        assign(delivered);
        mvc.perform(post("/api/v1/remittances/{id}/deliver", delivered).header("Authorization", bearer(courierToken)))
                .andExpect(status().isOk());
        assign(withIncident);
        mvc.perform(post("/api/v1/remittances/{id}/incidents", withIncident)
                        .header("Authorization", bearer(courierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"NOT_HOME\"}"))
                .andExpect(status().isOk());
        YearMonth month = YearMonth.now(clock);

        monthly(month.toString(), salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month.toString()))
                .andExpect(jsonPath("$.from").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.to").value(month.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.remittances.total").value(4))
                .andExpect(jsonPath("$.remittances.delivery").value(3))
                .andExpect(jsonPath("$.remittances.pickup").value(1))
                .andExpect(jsonPath("$.remittances.completed").value(1))
                .andExpect(jsonPath("$.remittances.open").value(3))
                .andExpect(jsonPath("$.remittances.withIncident").value(1))
                .andExpect(jsonPath("$.collected").value(267.00))
                .andExpect(jsonPath("$.fees").value(50.00))
                .andExpect(jsonPath("$.deliveredUsd").value(236.95))
                .andExpect(jsonPath("$.difference").value(30.05))
                .andExpect(jsonPath("$.roundingMargin").value(0.05))
                .andExpect(jsonPath("$.pickups.remittances").value(1))
                .andExpect(jsonPath("$.pickups.collected").value(200.00))
                .andExpect(jsonPath("$.pickups.fees").value(20.00))
                .andExpect(jsonPath("$.delivered", hasSize(2)))
                .andExpect(jsonPath("$.delivered[0].currency").value("CUP"))
                .andExpect(jsonPath("$.delivered[0].amount").value(56150.00))
                .andExpect(jsonPath("$.delivered[1].currency").value("USD"))
                .andExpect(jsonPath("$.delivered[1].amount").value(100.00))
                .andExpect(jsonPath("$.byCurrency[0].currency").value("CUP"))
                .andExpect(jsonPath("$.byCurrency[0].remittances").value(2))
                .andExpect(jsonPath("$.byCurrency[0].sent").value(137.00))
                .andExpect(jsonPath("$.byCurrency[0].fees").value(20.00))
                .andExpect(jsonPath("$.byCurrency[0].collected").value(157.00))
                .andExpect(jsonPath("$.byCurrency[0].delivered").value(56150.00))
                .andExpect(jsonPath("$.byCurrency[0].deliveredUsd").value(136.95))
                .andExpect(jsonPath("$.byCurrency[1].currency").value("USD"))
                .andExpect(jsonPath("$.byCurrency[1].deliveredUsd").value(100.00))
                .andExpect(jsonPath("$.byType[0].type").value("DELIVERY"))
                .andExpect(jsonPath("$.byType[0].remittances").value(3))
                .andExpect(jsonPath("$.byType[0].collected").value(267.00))
                .andExpect(jsonPath("$.byType[0].fees").value(30.00))
                .andExpect(jsonPath("$.byType[1].type").value("PICKUP"))
                .andExpect(jsonPath("$.byType[1].collected").value(200.00))
                .andExpect(jsonPath("$.byType[1].fees").value(20.00));
    }

    @Test
    void aMonthWithoutRemittancesHasZerosAndEmptyLists() throws Exception {
        register("DELIVERY", "100", "CUP");

        monthly(YearMonth.now(clock).minusMonths(1).toString(), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittances.total").value(0))
                .andExpect(jsonPath("$.collected").value(0))
                .andExpect(jsonPath("$.delivered", hasSize(0)))
                .andExpect(jsonPath("$.byCurrency", hasSize(0)))
                .andExpect(jsonPath("$.byType", hasSize(0)));
    }

    @Test
    void theMonthIsRequiredAndOnlyAdminAndSalesSeeTheReport() throws Exception {
        monthly("2026-13", salesToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/reports/monthly").header("Authorization", bearer(salesToken)))
                .andExpect(status().isBadRequest());
        monthly(YearMonth.now(clock).toString(), courierToken).andExpect(status().isForbidden());
    }

    private String register(String type, String amount, String currency) throws Exception {
        return id(mvc.perform(post("/api/v1/remittances")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "customerId": "%s", "beneficiaryId": "%s", "amount": %s, "targetCurrency": "%s"}
                        """.formatted(type, customerId, beneficiaryId, amount, currency))));
    }

    private void assign(String id) throws Exception {
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\": \"" + courier.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions monthly(String month, String token) throws Exception {
        return mvc.perform(get("/api/v1/reports/monthly").param("month", month).header("Authorization", bearer(token)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
