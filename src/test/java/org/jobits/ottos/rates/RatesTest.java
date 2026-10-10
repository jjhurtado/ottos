package org.jobits.ottos.rates;

import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RatesTest extends ApiTestSupport {

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
    }

    @Test
    void quoteAppliesTheMinimumFeeAndRoundsCupDownToFifty() throws Exception {
        setRate("USD-CUP", "410").andExpect(status().isCreated());

        // 37 × 410 = 15 170 CUP → 15 150; 10 % of 37 is 3.70, below the 10 USD minimum
        quote("USD", "CUP", "37")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(10.00))
                .andExpect(jsonPath("$.total").value(47.00))
                .andExpect(jsonPath("$.rate").value(410))
                .andExpect(jsonPath("$.amountToDeliver").value(15150.00));
    }

    @Test
    void quoteAppliesThePercentageAboveTheMinimum() throws Exception {
        setRate("USD-CUP", "410").andExpect(status().isCreated());

        quote("USD", "CUP", "230")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(23.00))
                .andExpect(jsonPath("$.total").value(253.00))
                .andExpect(jsonPath("$.amountToDeliver").value(94300.00));
    }

    @Test
    void usdToUsdIsNotRounded() throws Exception {
        quote("USD", "USD", "137.45")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(13.75))
                .andExpect(jsonPath("$.total").value(151.20))
                .andExpect(jsonPath("$.amountToDeliver").value(137.45));
    }

    @Test
    void quoteWithoutARateReturns409() throws Exception {
        quote("USD", "CUP", "100").andExpect(status().isConflict());
    }

    @Test
    void unknownCorridorAndBadAmountsReturn400() throws Exception {
        quote("EUR", "CUP", "100").andExpect(status().isBadRequest());
        quote("USD", "USD", "0").andExpect(status().isBadRequest());
        quote("USD", "USD", "10.123").andExpect(status().isBadRequest());
    }

    @Test
    void theNewestRateAndFeeRuleWin() throws Exception {
        setRate("USD-CUP", "400").andExpect(status().isCreated());
        setRate("USD-CUP", "420").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/fee-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"FIXED\", \"value\": 12}"))
                .andExpect(status().isCreated());

        quote("USD", "CUP", "100")
                .andExpect(jsonPath("$.rate").value(420))
                .andExpect(jsonPath("$.fee").value(12.00))
                .andExpect(jsonPath("$.amountToDeliver").value(42000.00));

        mvc.perform(get("/api/v1/configuration/corridors/USD-CUP/rates").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rate").value(420))
                .andExpect(jsonPath("$[1].rate").value(400));
        mvc.perform(get("/api/v1/configuration/corridors").header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("USD-CUP"))
                .andExpect(jsonPath("$[0].currentRate.rate").value(420))
                .andExpect(jsonPath("$[0].currentFeeRule.type").value("FIXED"));
    }

    @Test
    void theConfiguredMinimumAmountApplies() throws Exception {
        mvc.perform(put("/api/v1/configuration/settings")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minimumAmount\": 20}"))
                .andExpect(status().isOk());

        quote("USD", "USD", "19.99")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_BELOW_MINIMUM"))
                .andExpect(jsonPath("$.minimumAmount").value(20.00))
                .andExpect(jsonPath("$.currency").value("USD"));
        quote("USD", "USD", "20").andExpect(status().isOk());
    }

    @Test
    void sameCurrencyRateMustBeOne() throws Exception {
        setRate("USD-USD", "1.5").andExpect(status().isBadRequest());
    }

    @Test
    void salesCanQuoteButNotSetRates() throws Exception {
        mvc.perform(post("/api/v1/configuration/corridors/USD-CUP/rates")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\": 500}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions setRate(String corridor, String rate) throws Exception {
        return mvc.perform(post("/api/v1/configuration/corridors/{code}/rates", corridor)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\": " + rate + "}"));
    }

    private ResultActions quote(String source, String target, String amount) throws Exception {
        return mvc.perform(post("/api/v1/quotes")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceCurrency": "%s", "targetCurrency": "%s", "amount": %s}
                        """.formatted(source, target, amount)));
    }
}
