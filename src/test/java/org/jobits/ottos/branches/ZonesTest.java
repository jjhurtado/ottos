package org.jobits.ottos.branches;

import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ZonesTest extends ApiTestSupport {

    @Test
    void laHabanaHasItsFifteenMunicipalities() throws Exception {
        String token = tokenFor("delivery@ottos.test", "DELIVERY");

        mvc.perform(get("/api/v1/provinces").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("La Habana"));
        mvc.perform(get("/api/v1/municipalities").param("province", "23").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$[0].name").value("Arroyo Naranjo"))
                .andExpect(jsonPath("$[0].provinceName").value("La Habana"));
    }
}
