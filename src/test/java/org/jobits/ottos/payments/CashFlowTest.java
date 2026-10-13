package org.jobits.ottos.payments;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CashFlowTest extends ApiTestSupport {

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
    void cashFollowsPaymentsFundingDeliveriesPickupsAndReturns() throws Exception {
        cash("/api/v1/cash/business/deposits", adminToken, "100000", "CUP", "Compra de CUP").andExpect(status().isCreated());
        cash(courierPath("funding"), salesToken, "50000", "CUP", null).andExpect(status().isCreated());

        String delivery = completed("DELIVERY", "100");   // charged 110 USD abroad, hands over 41 000 CUP
        String pickup = completed("PICKUP", "200");       // collects 200 USD

        mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(courierToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'CUP')].amount").value(9000.00))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'USD')].amount").value(200.00))
                .andExpect(jsonPath("$.cash.negative").value(false))
                .andExpect(jsonPath("$.movements", hasSize(3)))
                .andExpect(jsonPath("$.movements[0].type").value("PICKUP_COLLECTION"))
                .andExpect(jsonPath("$.movements[1].signedAmount").value(-41000.00));

        cash(courierPath("returns"), salesToken, "9000", "CUP", "Fin del día").andExpect(status().isCreated());
        cash(courierPath("returns"), salesToken, "200", "USD", "Fin del día").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/cash/couriers/{id}", courier.getId()).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'CUP')].amount").value(0.00))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'USD')].amount").value(0.00));
        mvc.perform(get("/api/v1/cash/business").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$[?(@.currency == 'CUP')].amount").value(59000.00))
                // What senders pay abroad never enters the box: it holds the cash in Cuba (here, the pickup's 200 USD)
                .andExpect(jsonPath("$[?(@.currency == 'USD')].amount").value(200.00));
        mvc.perform(get("/api/v1/cash/remittances/{id}/movements", delivery).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("DELIVERY_PAYOUT"));
        mvc.perform(get("/api/v1/cash/remittances/{id}/movements", pickup).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("PICKUP_COLLECTION"));
    }

    @Test
    void aCourierCanDeliverWithoutCashAndGoesNegative() throws Exception {
        completed("DELIVERY", "100");

        createUser("new.courier@ottos.test", "DELIVERY");

        // A new courier without movements is listed too, so Sales can give them their first funds
        mvc.perform(get("/api/v1/cash/couriers").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("User courier@ottos.test"))
                .andExpect(jsonPath("$[0].balances[0].amount").value(-41000.00))
                .andExpect(jsonPath("$[0].negative").value(true))
                .andExpect(jsonPath("$[1].name").value("User new.courier@ottos.test"))
                .andExpect(jsonPath("$[1].balances", hasSize(0)))
                .andExpect(jsonPath("$[1].negative").value(false));
    }

    @Test
    void whoCanMoveCash() throws Exception {
        cash(courierPath("funding"), courierToken, "100", "USD", null).andExpect(status().isForbidden());
        cash("/api/v1/cash/business/deposits", salesToken, "100", "USD", "Capital").andExpect(status().isForbidden());
        cash("/api/v1/cash/business/deposits", adminToken, "100", "USD", null).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cash/business").header("Authorization", bearer(courierToken)))
                .andExpect(status().isForbidden());

        User sales = users.findByEmail("sales@ottos.test").orElseThrow();
        cash("/api/v1/cash/couriers/" + sales.getId() + "/funding", salesToken, "100", "USD", null)
                .andExpect(status().isBadRequest());
    }

    /** Registers a remittance, assigns it to the courier and completes it; returns its id. */
    @Test
    void voidingADeliveredRemittanceReversesItsCashAndItStopsCounting() throws Exception {
        String delivery = completed("DELIVERY", "100");   // hands over 41 000 CUP
        String month = YearMonth.now(clock).toString();

        voidIt(delivery, salesToken, "Sales cannot").andExpect(status().isForbidden());
        voidIt(delivery, adminToken, "Era una entrega de efectivo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided.reason").value("Era una entrega de efectivo"))
                .andExpect(jsonPath("$.voided.byName").value("User admin@ottos.test"));

        mvc.perform(get("/api/v1/cash/couriers/{id}", courier.getId()).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'CUP')].amount").value(0.00));
        mvc.perform(get("/api/v1/cash/remittances/{id}/movements", delivery).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].type").value("REMITTANCE_VOID_REVERSAL"));
        mvc.perform(get("/api/v1/reports/monthly").param("month", month).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.remittances.total").value(0));
        mvc.perform(get("/api/v1/remittances").param("voided", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/audit").param("action", "REMITTANCE_VOIDED").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(1));

        voidIt(delivery, adminToken, "otra vez")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMITTANCE_ALREADY_VOIDED"));
    }

    @Test
    void aVoidedOpenRemittanceLeavesTheCourierAndCannotBeDelivered() throws Exception {
        String id = id(mvc.perform(post("/api/v1/remittances").header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 100, "targetCurrency": "CUP"}
                        """.formatted(customerId, beneficiaryId))));
        mvc.perform(post("/api/v1/remittances/{id}/assign", id).header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\": \"" + courier.getId() + "\"}"));

        voidIt(id, adminToken, "Duplicada").andExpect(status().isOk());

        mvc.perform(get("/api/v1/remittances/assigned").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMITTANCE_VOIDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments.cash_movements WHERE remittance_id = ?::uuid",
                Integer.class, id)).isZero();
    }

    private ResultActions voidIt(String id, String token, String reason)
            throws Exception {
        return mvc.perform(post("/api/v1/remittances/{id}/void", id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + reason + "\"}"));
    }

    private String completed(String type, String amount) throws Exception {
        String id = id(mvc.perform(post("/api/v1/remittances")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "customerId": "%s", "beneficiaryId": "%s", "amount": %s, "targetCurrency": "CUP"}
                        """.formatted(type, customerId, beneficiaryId, amount))));
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\": \"" + courier.getId() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(courierToken)))
                .andExpect(status().isOk());
        return id;
    }

    private String courierPath(String action) {
        return "/api/v1/cash/couriers/" + courier.getId() + "/" + action;
    }

    private ResultActions cash(String path, String token, String amount, String currency, String note) throws Exception {
        String noteJson = note == null ? "" : ", \"note\": \"" + note + "\"";
        return mvc.perform(post(path)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + ", \"currency\": \"" + currency + "\"" + noteJson + "}"));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
