package org.jobits.ottos.sync;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The offline-first mobile app's contract: docs/contracts/offline-sync.md. */
class SyncTest extends ApiTestSupport {

    private String adminToken;
    private String salesToken;
    private String courierToken;
    private User courier;
    private User otherCourier;
    private String otherCourierToken;
    private String customerId;
    private String beneficiaryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
        courier = createUser("courier@ottos.test", "DELIVERY");
        courierToken = accessToken("courier@ottos.test");
        otherCourier = createUser("other.courier@ottos.test", "DELIVERY");
        otherCourierToken = accessToken("other.courier@ottos.test");

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

    // ---------------------------------------------------------------- download

    @Test
    void theFirstDownloadBringsEverythingTheCourierNeedsOffline() throws Exception {
        String mine = registered();
        assign(mine, courier);
        assign(registered(), otherCourier);

        state(courierToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.full").value(true))
                .andExpect(jsonPath("$.remittances", hasSize(1)))
                .andExpect(jsonPath("$.remittances[0].id").value(mine))
                .andExpect(jsonPath("$.remittances[0].pin").doesNotExist())
                .andExpect(jsonPath("$.removedIds", hasSize(0)))
                .andExpect(jsonPath("$.cash").exists())
                .andExpect(jsonPath("$.workflow.statuses").isArray());
    }

    @Test
    void salesGetNoRemittancesNorCashBecauseTheyWorkOnline() throws Exception {
        assign(registered(), courier);

        state(salesToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittances", hasSize(0)))
                .andExpect(jsonPath("$.cash").doesNotExist());
    }

    @Test
    void laterDownloadsBringOnlyWhatChangedAndWhatToRemove() throws Exception {
        String unchanged = registered();
        String changed = registered();
        String reassigned = registered();
        for (String id : new String[] {unchanged, changed, reassigned}) {
            assign(id, courier);
        }
        String since = JsonPath.read(body(state(courierToken, null)), "$.serverTime");
        Thread.sleep(5);

        reportIncident(changed, "NOT_HOME", salesToken).andExpect(status().isOk());
        assign(reassigned, otherCourier);

        state(courierToken, since)
                .andExpect(jsonPath("$.full").value(false))
                .andExpect(jsonPath("$.remittances", hasSize(1)))
                .andExpect(jsonPath("$.remittances[0].id").value(changed))
                .andExpect(jsonPath("$.remittances[0].incident.reason").value("NOT_HOME"))
                .andExpect(jsonPath("$.removedIds", hasSize(1)))
                .andExpect(jsonPath("$.removedIds[0]").value(reassigned));
    }

    // ---------------------------------------------------------------- actions

    @Test
    void anOfflineDeliveryIsAppliedAtTheTimeItHappenedAndOnlyOnce() throws Exception {
        String id = registered();
        assign(id, courier);
        clock.advance(Duration.ofHours(3));
        Instant deliveredAt = clock.instant().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        String actionId = UUID.randomUUID().toString();
        String deliver = action(actionId, "DELIVER", id, deliveredAt, "{\"note\": \"Sin cobertura\"}");

        sync(courierToken, deliver)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(actionId))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].flaggedForReview").value(false))
                .andExpect(jsonPath("$.results[0].remittance.status").value("DELIVERED"));

        String detail = body(mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(salesToken))));
        assertThat(Instant.parse(JsonPath.read(detail, "$.completedAt"))).isEqualTo(deliveredAt);
        String cash = body(mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(courierToken))));
        assertThat(Instant.parse(JsonPath.read(cash, "$.movements[0].occurredAt"))).isEqualTo(deliveredAt);

        // The response was lost and the app sends it again
        sync(courierToken, deliver)
                .andExpect(jsonPath("$.results[0].status").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.results[0].remittance.status").value("DELIVERED"));
        mvc.perform(get("/api/v1/cash/remittances/{id}/movements", id).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void salesHearOfADeliverySyncedLateEvenIfItHappenedBeforeTheirLastCheck() throws Exception {
        String id = registered();
        assign(id, courier);
        clock.advance(Duration.ofHours(3));
        // Without since: nothing from the past, only where to start
        String first = body(completed(salesToken, null)
                .andExpect(jsonPath("$.remittances", hasSize(0)))
                .andExpect(jsonPath("$.hasMore").value(false)));
        String since = JsonPath.read(first, "$.serverTime");

        Instant deliveredAt = clock.instant().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS);
        sync(courierToken, action(UUID.randomUUID().toString(), "DELIVER", id, deliveredAt, "{}"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
        clock.advance(Duration.ofMinutes(1));

        String feed = body(completed(salesToken, since)
                .andExpect(jsonPath("$.remittances", hasSize(1)))
                .andExpect(jsonPath("$.remittances[0].id").value(id))
                .andExpect(jsonPath("$.remittances[0].status").value("DELIVERED")));
        assertThat(Instant.parse(JsonPath.read(feed, "$.serverTime"))).isAfter(Instant.parse(since));
        completed(salesToken, JsonPath.read(feed, "$.serverTime")).andExpect(jsonPath("$.remittances", hasSize(0)));
        completed(courierToken, since).andExpect(status().isForbidden());
    }

    private ResultActions completed(String token, String since) throws Exception {
        var request = get("/api/v1/remittances/completed").header("Authorization", bearer(token));
        return mvc.perform(since == null ? request : request.param("since", since));
    }

    @Test
    void anActionIdCannotBeReusedForAnotherAction() throws Exception {
        String first = registered();
        String second = registered();
        assign(first, courier);
        assign(second, courier);
        String actionId = UUID.randomUUID().toString();
        sync(courierToken, action(actionId, "REPORT_INCIDENT", first, clock.instant(), "{\"reason\": \"NOT_HOME\"}"));

        sync(courierToken, action(actionId, "REPORT_INCIDENT", second, clock.instant(), "{\"reason\": \"NOT_HOME\"}"))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("ACTION_ID_CONFLICT"));
    }

    @Test
    void occurredAtCannotBeInTheFutureNorBeforeTheRemittance() throws Exception {
        String id = registered();
        assign(id, courier);

        sync(courierToken,
                action(UUID.randomUUID().toString(), "DELIVER", id, clock.instant().plus(Duration.ofHours(1)), "{}"),
                action(UUID.randomUUID().toString(), "DELIVER", id, clock.instant().minus(Duration.ofDays(1)), "{}"))
                .andExpect(jsonPath("$.results[0].code").value("INVALID_OCCURRED_AT"))
                .andExpect(jsonPath("$.results[1].code").value("INVALID_OCCURRED_AT"));
    }

    @Test
    void aVeryLateDeliveryIsKeptForReviewButALatePostponementIsRejected() throws Exception {
        String delivered = registered();
        String postponed = registered();
        assign(delivered, courier);
        assign(postponed, courier);
        Instant happened = clock.instant().plus(Duration.ofHours(1));
        clock.advance(Duration.ofDays(8));

        sync(courierToken,
                action(UUID.randomUUID().toString(), "DELIVER", delivered, happened, "{}"),
                action(UUID.randomUUID().toString(), "POSTPONE", postponed, happened,
                        "{\"newDate\": \"" + LocalDate.now(clock).plusDays(2) + "\", \"reason\": \"Nadie en casa\"}"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].flaggedForReview").value(true))
                .andExpect(jsonPath("$.results[0].remittance.review.reason").value("LATE_SYNC"))
                .andExpect(jsonPath("$.results[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[1].code").value("INVALID_OCCURRED_AT"));
    }

    @Test
    void aDeliveryAfterAReassignmentCountsForWhoeverHadItThen() throws Exception {
        String id = registered();
        assign(id, courier);
        Instant deliveredOffline = clock.instant().plus(Duration.ofMinutes(10));
        clock.advance(Duration.ofHours(1));
        assign(id, otherCourier);

        sync(courierToken, action(UUID.randomUUID().toString(), "DELIVER", id, deliveredOffline, "{}"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].flaggedForReview").value(true))
                .andExpect(jsonPath("$.results[0].remittance.review.reason").value("DELIVERED_AFTER_REASSIGNMENT"))
                .andExpect(jsonPath("$.results[0].remittance.courierId").value(courier.getId().toString()));

        // The cash left the courier who delivered, not the one it was reassigned to
        mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(courierToken)))
                .andExpect(jsonPath("$.cash.balances[0].amount").value(-41000.00));
        mvc.perform(get("/api/v1/remittances").param("review", "true").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void aCourierWhoNeverHadTheRemittanceCannotDeliverIt() throws Exception {
        String id = registered();
        assign(id, courier);
        createUser("third.courier@ottos.test", "DELIVERY");

        sync(accessToken("third.courier@ottos.test"),
                action(UUID.randomUUID().toString(), "DELIVER", id, clock.instant(), "{}"))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("REMITTANCE_NOT_FOUND"));
    }

    @Test
    void aDeliveryOfAlreadyDeliveredRemittanceIsRejectedAndFlagged() throws Exception {
        String id = registered();
        assign(id, courier);
        Instant deliveredOffline = clock.instant().plus(Duration.ofMinutes(10));
        clock.advance(Duration.ofHours(1));
        assign(id, otherCourier);
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(otherCourierToken)))
                .andExpect(status().isOk());

        sync(courierToken, action(UUID.randomUUID().toString(), "DELIVER", id, deliveredOffline, "{}"))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("REMITTANCE_ALREADY_COMPLETED"));
        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.review.reason").value("DUPLICATE_DELIVERY"));
        mvc.perform(get("/api/v1/remittances/{id}/events", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$[*].reviewReason", hasItem("DUPLICATE_DELIVERY")));
    }

    @Test
    void aWrongPinIsRejectedWhenStrictAndFlaggedWhenQueuedOffline() throws Exception {
        String strict = registered();
        String offline = registered();
        assign(strict, courier);
        assign(offline, courier);
        String wrong = "000000".equals(pin(strict)) ? "111111" : "000000";
        String wrongOffline = "000000".equals(pin(offline)) ? "111111" : "000000";

        sync(courierToken,
                action(UUID.randomUUID().toString(), "DELIVER_WITH_PIN", strict, clock.instant(),
                        "{\"pin\": \"" + wrong + "\", \"strictPin\": true}"),
                action(UUID.randomUUID().toString(), "DELIVER_WITH_PIN", offline, clock.instant(),
                        "{\"pin\": \"" + wrongOffline + "\"}"))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("INCORRECT_PIN"))
                .andExpect(jsonPath("$.results[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].remittance.review.reason").value("PIN_MISMATCH"));

        sync(courierToken, action(UUID.randomUUID().toString(), "DELIVER_WITH_PIN", strict, clock.instant(),
                "{\"pin\": \"" + pin(strict) + "\", \"strictPin\": true}"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].flaggedForReview").value(false));
    }

    @Test
    void postponementsAndIncidentsNeedTheRemittanceToBeTheirsNow() throws Exception {
        String mine = registered();
        String reassigned = registered();
        assign(mine, courier);
        assign(reassigned, courier);
        assign(reassigned, otherCourier);
        LocalDate newDate = LocalDate.now(clock).plusDays(4);

        sync(courierToken,
                action(UUID.randomUUID().toString(), "POSTPONE", mine, clock.instant(),
                        "{\"newDate\": \"" + newDate + "\", \"reason\": \"Nadie en casa\"}"),
                action(UUID.randomUUID().toString(), "REPORT_INCIDENT", mine, clock.instant(), "{\"reason\": \"UNREACHABLE\"}"),
                action(UUID.randomUUID().toString(), "REPORT_INCIDENT", reassigned, clock.instant(), "{\"reason\": \"NOT_HOME\"}"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].remittance.expectedDate").value(newDate.toString()))
                .andExpect(jsonPath("$.results[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].remittance.incident.reason").value("UNREACHABLE"))
                .andExpect(jsonPath("$.results[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[2].code").value("REMITTANCE_NOT_FOUND"));
    }

    @Test
    void aBadPayloadRejectsOnlyItsActionAndPermissionsStillApply() throws Exception {
        String id = registered();
        assign(id, courier);

        sync(courierToken,
                action(UUID.randomUUID().toString(), "DELIVER_WITH_PIN", id, clock.instant(), "{}"),
                action(UUID.randomUUID().toString(), "REPORT_INCIDENT", id, clock.instant(), "{\"reason\": \"LOST\"}"),
                action(UUID.randomUUID().toString(), "DELIVER", id, clock.instant(), "{}"))
                .andExpect(jsonPath("$.results[0].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[1].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[2].status").value("APPLIED"));

        String other = registered();
        assign(other, courier);
        sync(salesToken, action(UUID.randomUUID().toString(), "DELIVER", other, clock.instant(), "{}"))
                .andExpect(jsonPath("$.results[0].code").value("FORBIDDEN"));
    }

    @Test
    void anInvalidBatchAppliesNothing() throws Exception {
        String id = registered();
        assign(id, courier);
        String repeated = UUID.randomUUID().toString();

        sync(courierToken,
                action(repeated, "DELIVER", id, clock.instant(), "{}"),
                action(repeated, "DELIVER", id, clock.instant(), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        String[] tooMany = Collections.nCopies(201, "x").stream()
                .map(x -> action(UUID.randomUUID().toString(), "DELIVER", id, clock.instant(), "{}"))
                .toArray(String[]::new);
        sync(courierToken, tooMany).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/remittances/{id}", id).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.status").value(not("DELIVERED")));
    }

    // ---------------------------------------------------------------- helpers

    private String registered() throws Exception {
        return id(mvc.perform(post("/api/v1/remittances")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 100, "targetCurrency": "CUP"}
                        """.formatted(customerId, beneficiaryId))));
    }

    private String pin(String id) throws Exception {
        return JsonPath.read(body(mvc.perform(get("/api/v1/remittances/{id}", id)
                .header("Authorization", bearer(salesToken)))), "$.pin");
    }

    private void assign(String id, User to) throws Exception {
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\": \"" + to.getId() + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions reportIncident(String id, String reason, String token) throws Exception {
        return mvc.perform(post("/api/v1/remittances/{id}/incidents", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}"));
    }

    private ResultActions state(String token, String since) throws Exception {
        var request = get("/api/v1/sync/state").header("Authorization", bearer(token));
        if (since != null) {
            request.param("since", since);
        }
        return mvc.perform(request);
    }

    private static String action(String id, String type, String remittanceId, Instant occurredAt, String payload) {
        return """
                {"id": "%s", "type": "%s", "remittanceId": "%s", "occurredAt": "%s", "payload": %s}
                """.formatted(id, type, remittanceId, occurredAt, payload);
    }

    private ResultActions sync(String token, String... actions) throws Exception {
        return mvc.perform(post("/api/v1/sync/actions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\": \"test-phone\", \"actions\": [" + String.join(",", actions) + "]}"));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
    }
}
