package org.jobits.ottos.configuration;

import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigurationTest extends ApiTestSupport {

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
    }

    @Test
    void everythingIsReadInOneCall() throws Exception {
        mvc.perform(get("/api/v1/configuration").header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.minimumAmount").value(0.00))
                .andExpect(jsonPath("$.settings.minimumAmountCurrency").value("USD"))
                .andExpect(jsonPath("$.settings.defaultDeliveryDays").value(2))
                .andExpect(jsonPath("$.corridors[0].code").value("USD-CUP"))
                .andExpect(jsonPath("$.corridors[0].currentFeeRule.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.corridors[0].currentPickupRate.rate").value(1))
                .andExpect(jsonPath("$.corridors[0].currentPickupRate.remittanceType").value("PICKUP"))
                .andExpect(jsonPath("$.corridors[0].currentPickupFeeRule.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.corridors[1].code").value("USD-USD"))
                .andExpect(jsonPath("$.corridors[1].currentRate.rate").value(1));
    }

    @Test
    void settingsChangeOneAtATime() throws Exception {
        updateSettings(adminToken, "{\"minimumAmount\": 20}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumAmount").value(20.00))
                .andExpect(jsonPath("$.defaultDeliveryDays").value(2));
        updateSettings(adminToken, "{\"defaultDeliveryDays\": 3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumAmount").value(20.00))
                .andExpect(jsonPath("$.defaultDeliveryDays").value(3));

        mvc.perform(get("/api/v1/configuration/settings").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.minimumAmount").value(20.00))
                .andExpect(jsonPath("$.defaultDeliveryDays").value(3));
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        updateSettings(adminToken, "{\"minimumAmount\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        updateSettings(adminToken, "{\"minimumAmount\": 10.123}").andExpect(status().isBadRequest());
        updateSettings(adminToken, "{\"defaultDeliveryDays\": 0}").andExpect(status().isBadRequest());
    }

    @Test
    void salesCanReadButNotChangeTheConfiguration() throws Exception {
        updateSettings(salesToken, "{\"minimumAmount\": 20}").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/configuration/corridors/USD-CUP/rates").header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk());
    }

    @Test
    void historiesCanBeNarrowedToOneKindOfRemittance() throws Exception {
        mvc.perform(get("/api/v1/configuration/corridors/USD-CUP/rates").param("remittanceType", "PICKUP")
                        .header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].remittanceType").value("PICKUP"))
                .andExpect(jsonPath("$[0].rate").value(1));
        mvc.perform(get("/api/v1/configuration/corridors/USD-CUP/fee-rules").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void couriersCannotReadTheConfiguration() throws Exception {
        String courierToken = tokenFor("courier@ottos.test", "DELIVERY");

        mvc.perform(get("/api/v1/configuration").header("Authorization", bearer(courierToken)))
                .andExpect(status().isForbidden());
    }

    private ResultActions updateSettings(String token, String json) throws Exception {
        return mvc.perform(put("/api/v1/configuration/settings")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
