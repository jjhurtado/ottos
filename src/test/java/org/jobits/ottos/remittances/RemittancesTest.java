package org.jobits.ottos.remittances;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RemittancesTest extends ApiTestSupport {

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

        mvc.perform(post("/api/v1/corridors/USD-CUP/rates")
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
                        {"fullName": "Rosa Martínez", "phone": "5352223344", "street": "Calle 10", "houseNumber": "512",
                         "municipalityCode": "2302"}
                        """)));
    }

    @Test
    void aNewDeliveryIsPaidWithTheQuoteCopiedAndDueInTwoDays() throws Exception {
        register("DELIVERY", "37", "CUP")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("OT-[A-Z2-9]{6}")))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.statusName").value("Pagada"))
                .andExpect(jsonPath("$.customerName").value("Ana Pérez"))
                .andExpect(jsonPath("$.beneficiaryName").value("Rosa Martínez"))
                .andExpect(jsonPath("$.beneficiaryAddress").value("Calle 10 #512, Plaza de la Revolución, La Habana"))
                .andExpect(jsonPath("$.amount").value(37.00))
                .andExpect(jsonPath("$.fee").value(10.00))
                .andExpect(jsonPath("$.total").value(47.00))
                .andExpect(jsonPath("$.amountToDeliver").value(15150.00))
                .andExpect(jsonPath("$.cashCurrency").value("CUP"))
                .andExpect(jsonPath("$.pin").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.expectedDate").value(LocalDate.now(clock).plusDays(2).toString()))
                .andExpect(jsonPath("$.late").value(false));
    }

    @Test
    void theSameIdempotencyKeyReturnsTheFirstRemittance() throws Exception {
        String first = id(register("DELIVERY", "100", "USD", "key-1"));
        String second = id(register("DELIVERY", "100", "USD", "key-1"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void theBeneficiaryMustBeLinkedToTheCustomer() throws Exception {
        String other = id(mvc.perform(post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Luis Gómez\", \"phone\": \"+13055550202\"}")));

        mvc.perform(post("/api/v1/remittances")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 50,
                                 "targetCurrency": "CUP"}
                                """.formatted(other, beneficiaryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignDeliverAndKeepTheHistory() throws Exception {
        String id = id(register("DELIVERY", "100", "CUP"));

        assign(id, courier.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.courierName").value("User courier@ottos.test"));

        // The courier sees what to hand over, but not the PIN or the money breakdown
        mvc.perform(get("/api/v1/remittances/assigned").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cashAmount").value(41000.00))
                .andExpect(jsonPath("$[0].cashCurrency").value("CUP"))
                .andExpect(jsonPath("$[0].pin").doesNotExist())
                .andExpect(jsonPath("$[0].fee").doesNotExist())
                .andExpect(jsonPath("$[0].customerName").doesNotExist());

        mvc.perform(post("/api/v1/remittances/{id}/deliver", id)
                        .header("Authorization", bearer(courierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Entregado a la hija\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mvc.perform(get("/api/v1/remittances/assigned").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/remittances/{id}/events", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[1].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[2].toStatus").value("DELIVERED"))
                .andExpect(jsonPath("$[2].note").value("Entregado a la hija"));
    }

    @Test
    void couriersOnlySeeAndActOnTheirOwnRemittances() throws Exception {
        createUser("other.courier@ottos.test", "DELIVERY");
        String otherToken = accessToken("other.courier@ottos.test");
        String id = id(register("DELIVERY", "100", "CUP"));
        assign(id, courier.getId().toString());

        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/remittances").header("Authorization", bearer(courierToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyCouriersCanBeAssignedAndTheyMustBeAssignedFirst() throws Exception {
        String id = id(register("DELIVERY", "100", "CUP"));
        User sales = users.findByEmail("sales@ottos.test").orElseThrow();

        assign(id, sales.getId().toString()).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void deliveryWithPinChecksIt() throws Exception {
        String body = register("DELIVERY", "100", "CUP").andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String pin = JsonPath.read(body, "$.pin");
        String wrong = pin.equals("000000") ? "111111" : "000000";
        assign(id, courier.getId().toString());

        deliverWithPin(id, wrong).andExpect(status().isBadRequest());
        deliverWithPin(id, pin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void pickupFinancialsAreOnlyForAdmins() throws Exception {
        String id = id(register("PICKUP", "200", "CUP"));
        assign(id, courier.getId().toString());

        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.type").value("PICKUP"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.fee").doesNotExist())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.cashAmount").value(200.00))
                .andExpect(jsonPath("$.cashCurrency").value("USD"));
        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.fee").value(20.00))
                .andExpect(jsonPath("$.rate").value(410));
        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$.cashAmount").value(200.00))
                .andExpect(jsonPath("$.fee").doesNotExist());
    }

    @Test
    void lateRemittancesCanBePostponedWithAReason() throws Exception {
        String id = id(register("DELIVERY", "100", "CUP"));
        assign(id, courier.getId().toString());
        clock.advance(Duration.ofDays(3));

        mvc.perform(get("/api/v1/remittances").param("late", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].late").value(true));

        LocalDate today = LocalDate.now(clock);
        postpone(id, today.minusDays(1), courierToken).andExpect(status().isBadRequest());
        postpone(id, today.plusDays(2), courierToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.postponements").value(1))
                .andExpect(jsonPath("$.expectedDate").value(today.plusDays(2).toString()));

        mvc.perform(get("/api/v1/remittances").param("late", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/remittances/{id}/events", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$[2].type").value("POSTPONED"))
                .andExpect(jsonPath("$[2].note").value("Nadie en casa"));
    }

    @Test
    void statusesAndTransitionsComeFromTheDatabase() throws Exception {
        jdbc.update("INSERT INTO remittance_statuses (code, name, position) VALUES ('ON_THE_WAY', 'En camino', 25)");
        jdbc.update("INSERT INTO remittance_transitions VALUES ('ASSIGNED', 'ON_THE_WAY', 'remittances:deliver')");
        jdbc.update("INSERT INTO remittance_transitions VALUES ('ON_THE_WAY', 'DELIVERED', 'remittances:deliver')");
        jdbc.update("DELETE FROM remittance_transitions WHERE from_status = 'ASSIGNED' AND to_status = 'DELIVERED'");
        try {
            String id = id(register("DELIVERY", "100", "CUP"));
            assign(id, courier.getId().toString());

            mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(courierToken)))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/v1/remittances/{id}/transitions", id)
                            .header("Authorization", bearer(courierToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toStatus\": \"ON_THE_WAY\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusName").value("En camino"));
            mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(courierToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));
        } finally {
            jdbc.update("DELETE FROM remittance_events");
            jdbc.update("DELETE FROM remittances");
            jdbc.update("DELETE FROM remittance_transitions WHERE 'ON_THE_WAY' IN (from_status, to_status)");
            jdbc.update("DELETE FROM remittance_statuses WHERE code = 'ON_THE_WAY'");
            jdbc.update("INSERT INTO remittance_transitions VALUES ('ASSIGNED', 'DELIVERED', 'remittances:deliver')");
        }
    }

    @Test
    void customerAndBeneficiaryStatistics() throws Exception {
        String first = id(register("DELIVERY", "100", "CUP"));
        register("DELIVERY", "37", "CUP");
        register("PICKUP", "50", "CUP");
        assign(first, courier.getId().toString());
        mvc.perform(post("/api/v1/remittances/{id}/deliver", first).header("Authorization", bearer(courierToken)));

        mvc.perform(get("/api/v1/customers/{id}/stats", customerId).header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittances").value(3))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.deliveries.count").value(2))
                .andExpect(jsonPath("$.deliveries.amountSent").value(137.00))
                .andExpect(jsonPath("$.deliveries.fees").value(20.00))
                .andExpect(jsonPath("$.deliveries.charged").value(157.00))
                .andExpect(jsonPath("$.deliveries.averageAmount").value(68.50))
                .andExpect(jsonPath("$.pickups.count").value(1))
                .andExpect(jsonPath("$.lastMonths", hasSize(12)))
                .andExpect(jsonPath("$.lastMonths[11].count").value(3))
                .andExpect(jsonPath("$.topBeneficiaries[0].name").value("Rosa Martínez"))
                .andExpect(jsonPath("$.topMunicipalities[0].name").value("Plaza de la Revolución"));

        mvc.perform(get("/api/v1/beneficiaries/{id}/stats", beneficiaryId).header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received[0].currency").value("CUP"))
                .andExpect(jsonPath("$.received[0].count").value(1))
                .andExpect(jsonPath("$.received[0].amount").value(41000.00))
                .andExpect(jsonPath("$.topSenders[0].name").value("Ana Pérez"));
    }

    private ResultActions register(String type, String amount, String currency) throws Exception {
        return register(type, amount, currency, null);
    }

    private ResultActions register(String type, String amount, String currency, String idempotencyKey) throws Exception {
        var request = post("/api/v1/remittances")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "customerId": "%s", "beneficiaryId": "%s", "amount": %s, "targetCurrency": "%s"}
                        """.formatted(type, customerId, beneficiaryId, amount, currency));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    private ResultActions assign(String id, String courierId) throws Exception {
        return mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\": \"" + courierId + "\"}"));
    }

    private ResultActions deliverWithPin(String id, String pin) throws Exception {
        return mvc.perform(post("/api/v1/remittances/{id}/deliver-with-pin", id)
                .header("Authorization", bearer(courierToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"" + pin + "\"}"));
    }

    private ResultActions postpone(String id, LocalDate date, String token) throws Exception {
        return mvc.perform(post("/api/v1/remittances/{id}/postpone", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newDate\": \"" + date + "\", \"reason\": \"Nadie en casa\"}"));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
