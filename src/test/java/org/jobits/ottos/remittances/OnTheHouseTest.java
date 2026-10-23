package org.jobits.ottos.remittances;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deliveries on the house, free of fee. Contract: docs/contracts/on-the-house.md. */
class OnTheHouseTest extends ApiTestSupport {

    private User admin;
    private String adminToken;
    private String salesToken;
    private String courierToken;
    private User courier;
    private String customerId;
    private String beneficiaryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = createUser("admin@ottos.test", "ADMIN");
        adminToken = accessToken("admin@ottos.test");
        salesToken = tokenFor("sales@ottos.test", "SALES");
        courier = createUser("courier@ottos.test", "DELIVERY");
        courierToken = accessToken("courier@ottos.test");

        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/rates")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\": 410}"))
                .andExpect(status().isCreated());
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

    // ------------------------------------------------------------------ registering

    @Test
    void anAdminRegistersADeliveryOnTheHouseWithTheRuleFeeWaived() throws Exception {
        // 37 USD → 15 150 CUP as always; the rule's fee would be 10 USD (its minimum)
        register(adminToken, "DELIVERY", "37", "CUP", ", \"onTheHouse\": true, \"onTheHouseReason\": \"  Su madre  \"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTheHouse").value(true))
                .andExpect(jsonPath("$.amount").value(37.00))
                .andExpect(jsonPath("$.fee").value(0))
                .andExpect(jsonPath("$.feeOverridden").value(false))
                .andExpect(jsonPath("$.total").value(37.00))
                .andExpect(jsonPath("$.rate").value(410))
                .andExpect(jsonPath("$.amountToDeliver").value(15150.00))
                .andExpect(jsonPath("$.waivedFee").value(10.00))
                .andExpect(jsonPath("$.onTheHouseReason").value("Su madre"));

        // A percentage fee: 10 % of 200
        register(adminToken, "DELIVERY", "200", "USD", ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.waivedFee").value(20.00));
    }

    @Test
    void salesNeedsThePermissionGrantedInRoles() throws Exception {
        register(salesToken, "DELIVERY", "100", "CUP", ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ON_THE_HOUSE_NOT_ALLOWED"));

        createRole("ON_THE_HOUSE", "remittances:on-the-house");
        createUser("sales2@ottos.test", "SALES", "ON_THE_HOUSE");
        register(accessToken("sales2@ottos.test"), "DELIVERY", "100", "CUP",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTheHouse").value(true))
                .andExpect(jsonPath("$.fee").value(0));
    }

    @Test
    void onlyDeliveriesWithAReasonAndNoFee() throws Exception {
        register(adminToken, "PICKUP", "100", "CUP", ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ON_THE_HOUSE_DELIVERY_ONLY"));
        register(adminToken, "DELIVERY", "100", "CUP", ", \"onTheHouse\": true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ON_THE_HOUSE_REASON_REQUIRED"));
        register(adminToken, "DELIVERY", "100", "CUP", ", \"onTheHouse\": true, \"onTheHouseReason\": \"   \"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ON_THE_HOUSE_REASON_REQUIRED"));
        register(adminToken, "DELIVERY", "100", "CUP",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"" + "x".repeat(501) + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register(adminToken, "DELIVERY", "100", "CUP", ", \"fee\": 0, \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEE_NOT_ALLOWED"));

        mvc.perform(get("/api/v1/remittances").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void theMinimumAmountAppliesAsAlways() throws Exception {
        mvc.perform(put("/api/v1/configuration/settings")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumAmount\": 20}"))
                .andExpect(status().isOk());

        register(adminToken, "DELIVERY", "19.99", "USD", ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_BELOW_MINIMUM"));
    }

    @Test
    void theSameIdempotencyKeyReturnsTheFirstRemittance() throws Exception {
        String body = """
                {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 100, "targetCurrency": "CUP",
                 "onTheHouse": true, "onTheHouseReason": "Familia"}
                """.formatted(customerId, beneficiaryId);
        String first = id(mvc.perform(post("/api/v1/remittances").header("Authorization", bearer(adminToken))
                .header("Idempotency-Key", "oth-1").contentType(MediaType.APPLICATION_JSON).content(body)));
        mvc.perform(post("/api/v1/remittances").header("Authorization", bearer(adminToken))
                        .header("Idempotency-Key", "oth-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.onTheHouse").value(true));

        audit().andExpect(jsonPath("$.totalItems").value(1));
    }

    // ------------------------------------------------------------------ compatibility

    @Test
    void aClientThatDoesNotSendTheFieldsWorksAsBefore() throws Exception {
        register(salesToken, "DELIVERY", "37", "CUP", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTheHouse").value(false))
                .andExpect(jsonPath("$.fee").value(10.00))
                .andExpect(jsonPath("$.total").value(47.00))
                .andExpect(jsonPath("$.amountToDeliver").value(15150.00))
                .andExpect(jsonPath("$.waivedFee").doesNotExist())
                .andExpect(jsonPath("$.onTheHouseReason").doesNotExist());
        // onTheHouse false: the reason is ignored, also without the permission
        register(salesToken, "DELIVERY", "37", "CUP", ", \"onTheHouse\": false, \"onTheHouseReason\": \"Familia\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTheHouse").value(false))
                .andExpect(jsonPath("$.fee").value(10.00))
                .andExpect(jsonPath("$.onTheHouseReason").doesNotExist());
        // A pickup with a fee of 0 entered by hand, as before
        register(adminToken, "PICKUP", "100", "CUP", ", \"fee\": 0")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTheHouse").value(false))
                .andExpect(jsonPath("$.fee").value(0));

        quote("{\"sourceCurrency\": \"USD\", \"targetCurrency\": \"CUP\", \"amount\": 37}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(10.00))
                .andExpect(jsonPath("$.total").value(47.00))
                .andExpect(jsonPath("$.onTheHouse").value(false))
                .andExpect(jsonPath("$.waivedFee").doesNotExist());
        audit().andExpect(jsonPath("$.totalItems").value(0));
    }

    // ------------------------------------------------------------------ quote

    @Test
    void theQuotePreviewsADeliveryOnTheHouse() throws Exception {
        quote("{\"sourceCurrency\": \"USD\", \"targetCurrency\": \"CUP\", \"amount\": 37, \"onTheHouse\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onTheHouse").value(true))
                .andExpect(jsonPath("$.fee").value(0))
                .andExpect(jsonPath("$.total").value(37.00))
                .andExpect(jsonPath("$.waivedFee").value(10.00))
                .andExpect(jsonPath("$.amountToDeliver").value(15150.00))
                .andExpect(jsonPath("$.feeRuleId").value("00000000-0000-0000-0002-000000000001"));
        quote("""
                {"sourceCurrency": "USD", "targetCurrency": "CUP", "amount": 37, "remittanceType": "PICKUP",
                 "onTheHouse": true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ON_THE_HOUSE_DELIVERY_ONLY"));
        quote("{\"sourceCurrency\": \"USD\", \"targetCurrency\": \"CUP\", \"amount\": 37, \"fee\": 1, \"onTheHouse\": true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEE_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------ who sees what

    @Test
    void everyoneSeesTheBadgeButOnlyTheFinancialsShowTheWaivedFeeAndReason() throws Exception {
        String id = id(register(adminToken, "DELIVERY", "100", "CUP",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\""));
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\": \"" + courier.getId() + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.onTheHouse").value(true))
                .andExpect(jsonPath("$.waivedFee").value(10.00))
                .andExpect(jsonPath("$.onTheHouseReason").value("Familia"));
        mvc.perform(get("/api/v1/remittances/assigned").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].onTheHouse").value(true))
                .andExpect(jsonPath("$[0].cashAmount").value(41000.00))
                .andExpect(jsonPath("$[0].waivedFee").doesNotExist())
                .andExpect(jsonPath("$[0].onTheHouseReason").doesNotExist())
                .andExpect(jsonPath("$[0].fee").doesNotExist());
        mvc.perform(get("/api/v1/sync/state").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$.remittances[0].onTheHouse").value(true))
                .andExpect(jsonPath("$.remittances[0].waivedFee").doesNotExist())
                .andExpect(jsonPath("$.remittances[0].onTheHouseReason").doesNotExist());
    }

    @Test
    void theListFiltersByOnTheHouse() throws Exception {
        String free = id(register(adminToken, "DELIVERY", "100", "CUP",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\""));
        String paid = id(register(salesToken, "DELIVERY", "100", "CUP", ""));

        list("true").andExpect(jsonPath("$.items[*].id", contains(free)));
        list("false").andExpect(jsonPath("$.items[*].id", contains(paid)));
        mvc.perform(get("/api/v1/remittances").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    // ------------------------------------------------------------------ audit and report

    @Test
    void registeringOnTheHouseIsAudited() throws Exception {
        String id = id(register(adminToken, "DELIVERY", "37", "CUP",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"Su madre\""));

        audit()
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].targetType").value("REMITTANCE"))
                .andExpect(jsonPath("$.items[0].targetId").value(id))
                .andExpect(jsonPath("$.items[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.items[0].details.type").value("DELIVERY"))
                .andExpect(jsonPath("$.items[0].details.amount").value(37.00))
                .andExpect(jsonPath("$.items[0].details.currency").value("USD"))
                .andExpect(jsonPath("$.items[0].details.waivedFee").value(10.00))
                .andExpect(jsonPath("$.items[0].details.customerId").value(customerId))
                .andExpect(jsonPath("$.items[0].details.customerName").value("Ana Pérez"))
                .andExpect(jsonPath("$.items[0].details.beneficiaryId").value(beneficiaryId))
                .andExpect(jsonPath("$.items[0].details.beneficiaryName").value("Rosa Martínez"))
                .andExpect(jsonPath("$.items[0].details.reason").value("Su madre"));
    }

    @Test
    void theMonthlyReportShowsTheOnTheHouseBlockWithoutTheVoidedOnes() throws Exception {
        register(salesToken, "DELIVERY", "100", "CUP", "");
        register(adminToken, "DELIVERY", "200", "CUP", ", \"onTheHouse\": true, \"onTheHouseReason\": \"Familia\"");
        String voided = id(register(adminToken, "DELIVERY", "37", "USD",
                ", \"onTheHouse\": true, \"onTheHouseReason\": \"Error\""));
        mvc.perform(post("/api/v1/remittances/{id}/void", voided)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Registrada por error\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/reports/monthly").param("month", YearMonth.now(clock).toString())
                        .header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittances.total").value(2))
                .andExpect(jsonPath("$.collected").value(310.00))
                .andExpect(jsonPath("$.fees").value(10.00))
                .andExpect(jsonPath("$.onTheHouse.remittances").value(1))
                .andExpect(jsonPath("$.onTheHouse.amount").value(200.00))
                .andExpect(jsonPath("$.onTheHouse.waivedFees").value(20.00));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions register(String token, String type, String amount, String currency, String extra)
            throws Exception {
        return mvc.perform(post("/api/v1/remittances")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "customerId": "%s", "beneficiaryId": "%s", "amount": %s, "targetCurrency": "%s"%s}
                        """.formatted(type, customerId, beneficiaryId, amount, currency, extra)));
    }

    private ResultActions quote(String json) throws Exception {
        return mvc.perform(post("/api/v1/quotes")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions list(String onTheHouse) throws Exception {
        return mvc.perform(get("/api/v1/remittances").param("onTheHouse", onTheHouse)
                .header("Authorization", bearer(salesToken)));
    }

    private ResultActions audit() throws Exception {
        return mvc.perform(get("/api/v1/audit").param("action", "REMITTANCE_ON_THE_HOUSE")
                .header("Authorization", bearer(adminToken)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
