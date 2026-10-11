package org.jobits.ottos;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The web panel calls the API from its own origin in staging and production (ottos.cors.allowed-origins). */
@TestPropertySource(properties = "ottos.cors.allowed-origins=https://panel.ottos.test, https://other.ottos.test")
class CorsTest extends ApiTestSupport {

    private static final String PANEL = "https://panel.ottos.test";

    @Test
    void theConfiguredPanelCanCallTheApi() throws Exception {
        mvc.perform(options("/api/v1/remittances")
                        .header("Origin", PANEL)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PANEL));

        String token = tokenFor("admin@ottos.test", "ADMIN");
        mvc.perform(get("/api/v1/auth/me").header("Origin", PANEL).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PANEL));
    }

    @Test
    void anUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/remittances")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
