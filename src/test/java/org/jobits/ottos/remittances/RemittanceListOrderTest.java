package org.jobits.ottos.remittances;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /remittances: newest first, and a stable order between remittances registered at the same instant. */
class RemittanceListOrderTest extends ApiTestSupport {

    @Test
    void remittancesRegisteredAtTheSameInstantGoByIdSoPagesNeverRepeatOne() throws Exception {
        String token = tokenFor("sales@ottos.test", "SALES");
        String customerId = id(mvc.perform(post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Ana Pérez\", \"phone\": \"+13055550101\"}")));
        String beneficiaryId = id(mvc.perform(post("/api/v1/customers/{c}/beneficiaries", customerId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Rosa Martínez", "phone": "5352223344", "street": "Calle 10", "municipalityCode": "2302"}
                        """)));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(id(mvc.perform(post("/api/v1/remittances")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 100, "targetCurrency": "USD"}
                            """.formatted(customerId, beneficiaryId)))));
        }
        Instant sameInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ids.subList(0, 4).forEach(id -> jdbc.update("UPDATE remittances.remittances SET created_at = ? WHERE id = ?",
                Timestamp.from(sameInstant), UUID.fromString(id)));
        jdbc.update("UPDATE remittances.remittances SET created_at = ? WHERE id = ?",
                Timestamp.from(sameInstant.plusSeconds(1)), UUID.fromString(ids.get(4)));

        List<String> pages = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            List<String> items = JsonPath.read(mvc.perform(get("/api/v1/remittances")
                            .param("page", String.valueOf(page)).param("size", "1")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.items[*].id");
            pages.addAll(items);
        }

        // The newest first; the rest, registered at the same instant, by id (PostgreSQL compares uuids as text)
        List<String> tied = new ArrayList<>(ids.subList(0, 4));
        tied.sort(Comparator.reverseOrder());
        List<String> expected = new ArrayList<>(List.of(ids.get(4)));
        expected.addAll(tied);
        assertThat(pages).containsExactlyElementsOf(expected);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
