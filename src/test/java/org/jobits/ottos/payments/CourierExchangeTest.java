package org.jobits.ottos.payments;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/courier-exchange.md. */
class CourierExchangeTest extends ApiTestSupport {

    private String adminToken;
    private String salesToken;
    private String courierToken;
    private User courier;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
        courier = createUser("courier@ottos.test", "DELIVERY");
        courierToken = accessToken("courier@ottos.test");
    }

    @Test
    void aCourierChangesUsdToCupAndTheirCashUpdatesAtOnce() throws Exception {
        setDeliveryRate("410");

        exchange(courierToken, "USD", "100", "CUP", "480", "Cambio en el agro")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toAmount").value(48000.00))
                .andExpect(jsonPath("$.configuredRate").value(410))
                .andExpect(jsonPath("$.deviationPercent").value(17.07))
                .andExpect(jsonPath("$.recordedBy").value(courier.getId().toString()))
                .andExpect(jsonPath("$.review").doesNotExist());

        mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'USD')].amount").value(-100.00))
                .andExpect(jsonPath("$.cash.balances[?(@.currency == 'CUP')].amount").value(48000.00))
                .andExpect(jsonPath("$.movements", hasSize(2)))
                .andExpect(jsonPath("$.movements[0].exchangeId").isNotEmpty())
                .andExpect(jsonPath("$.movements[0].rate").value(480))
                .andExpect(jsonPath("$.movements[0].note").value("Cambio en el agro"));
        // The business box does not change
        mvc.perform(get("/api/v1/cash/business/movements").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cupToUsdDividesByTheRateQuotedPerDollar() throws Exception {
        exchange(courierToken, "CUP", "100000", "USD", "480", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toAmount").value(208.33))
                .andExpect(jsonPath("$.configuredRate").doesNotExist());
    }

    @Test
    void anUnusualRateIsRecordedButFlaggedUntilSalesResolvesIt() throws Exception {
        setDeliveryRate("410");
        String id = JsonPath.read(body(exchange(courierToken, "USD", "100", "CUP", "600", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.review.reason").value("UNUSUAL_EXCHANGE_RATE"))
                .andExpect(jsonPath("$.deviationPercent").value(46.34))), "$.id");

        mvc.perform(get("/api/v1/cash/exchanges").param("review", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].courierName").value("User courier@ottos.test"));
        resolve(id, courierToken).andExpect(status().isForbidden());
        resolve(id, salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.resolvedNote").value("Tasa del día en el agro"))
                .andExpect(jsonPath("$.review.resolvedAt").isNotEmpty());
        mvc.perform(get("/api/v1/cash/exchanges").param("review", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(0));
        resolve(id, salesToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_NOT_PENDING"));
    }

    @Test
    void theToleranceIsAConfigurationSetting() throws Exception {
        setDeliveryRate("410");
        mvc.perform(put("/api/v1/configuration/settings")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeRateTolerancePercent\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeRateTolerancePercent").value(50.00));

        exchange(courierToken, "USD", "100", "CUP", "600", null).andExpect(jsonPath("$.review").doesNotExist());
    }

    @Test
    void currenciesAmountsAndRatesAreChecked() throws Exception {
        exchange(courierToken, "USD", "100", "USD", "1", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
        exchange(courierToken, "USD", "100", "EUR", "0.9", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
        exchange(courierToken, "USD", "10.123", "CUP", "480", null).andExpect(status().isBadRequest());
        exchange(courierToken, "USD", "100", "CUP", "0", null).andExpect(status().isBadRequest());
    }

    @Test
    void salesCanRecordAnExchangeForACourierButNotForThemselves() throws Exception {
        exchange(salesToken, "USD", "100", "CUP", "480", null).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/cash/couriers/{id}/exchanges", courier.getId())
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("USD", "50", "CUP", "480", "Lo cambió por teléfono")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courierId").value(courier.getId().toString()))
                .andExpect(jsonPath("$.recordedBy").value(users.findByEmail("sales@ottos.test").orElseThrow().getId().toString()));
        User sales = users.findByEmail("sales@ottos.test").orElseThrow();
        mvc.perform(post("/api/v1/cash/couriers/{id}/exchanges", sales.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("USD", "50", "CUP", "480", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COURIER"));
    }

    @Test
    void anOfflineExchangeIsComparedWithTheRateInForceWhenItHappened() throws Exception {
        setDeliveryRate("410");
        Instant happened = clock.instant().plus(Duration.ofMinutes(5));
        clock.advance(Duration.ofHours(1));
        setDeliveryRate("600");
        String actionId = UUID.randomUUID().toString();
        String action = """
                {"id": "%s", "type": "CASH_EXCHANGE", "remittanceId": null, "occurredAt": "%s",
                 "payload": {"fromCurrency": "USD", "fromAmount": 100, "toCurrency": "CUP", "rate": 600}}
                """.formatted(actionId, happened);

        sync(action)
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].remittance").doesNotExist())
                .andExpect(jsonPath("$.results[0].exchange.toAmount").value(60000.00))
                .andExpect(jsonPath("$.results[0].exchange.configuredRate").value(410))
                .andExpect(jsonPath("$.results[0].exchange.review.reason").value("UNUSUAL_EXCHANGE_RATE"))
                .andExpect(jsonPath("$.results[0].flaggedForReview").value(true));
        sync(action)
                .andExpect(jsonPath("$.results[0].status").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.results[0].exchange.id").isNotEmpty());
        mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$.movements", hasSize(2)));
    }

    @Test
    void aBadOfflineExchangeRejectsOnlyItself() throws Exception {
        sync("""
                {"id": "%s", "type": "CASH_EXCHANGE", "occurredAt": "%s", "payload": {"fromCurrency": "USD"}}
                """.formatted(UUID.randomUUID(), clock.instant()),
                """
                {"id": "%s", "type": "DELIVER", "occurredAt": "%s", "payload": {}}
                """.formatted(UUID.randomUUID(), clock.instant()),
                """
                {"id": "%s", "type": "CASH_EXCHANGE", "occurredAt": "%s",
                 "payload": {"fromCurrency": "USD", "fromAmount": 20, "toCurrency": "CUP", "rate": 480}}
                """.formatted(UUID.randomUUID(), clock.instant()))
                .andExpect(jsonPath("$.results[0].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[1].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[2].status").value("APPLIED"));
    }

    private void setDeliveryRate(String rate) throws Exception {
        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/rates")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\": " + rate + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions exchange(String token, String from, String amount, String to, String rate, String note)
            throws Exception {
        return mvc.perform(post("/api/v1/cash/me/exchanges")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(from, amount, to, rate, note)));
    }

    private ResultActions resolve(String id, String token) throws Exception {
        return mvc.perform(post("/api/v1/cash/exchanges/{id}/review/resolve", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"Tasa del día en el agro\"}"));
    }

    private ResultActions sync(String... actions) throws Exception {
        return mvc.perform(post("/api/v1/sync/actions")
                .header("Authorization", bearer(courierToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actions\": [" + String.join(",", actions) + "]}"));
    }

    private static String json(String from, String amount, String to, String rate, String note) {
        String noteJson = note == null ? "" : ", \"note\": \"" + note + "\"";
        return "{\"fromCurrency\": \"" + from + "\", \"fromAmount\": " + amount + ", \"toCurrency\": \"" + to
                + "\", \"rate\": " + rate + noteJson + "}";
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
