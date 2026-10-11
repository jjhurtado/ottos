package org.jobits.ottos.remittances;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Requests that really run at the same time against PostgreSQL: idempotency keys, optimistic locking, synced action
 * ids and the creation of a courier's first cash account must hold under races, not only one after another.
 */
class ConcurrencyTest extends ApiTestSupport {

    private static final int THREADS = 6;

    private String salesToken;
    private String adminToken;
    private User courier;
    private String courierToken;
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
                .content("{\"fullName\": \"Ana Pérez\", \"phone\": \"+13055550101\"}")).andReturn().getResponse());
        beneficiaryId = id(mvc.perform(post("/api/v1/customers/{c}/beneficiaries", customerId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Rosa Martínez", "phone": "5352223344", "street": "Calle 10", "municipalityCode": "2302"}
                        """)).andReturn().getResponse());
    }

    @Test
    void theSameIdempotencyKeyAtOnceCreatesOneRemittance() throws Exception {
        List<MockHttpServletResponse> responses = atOnce(THREADS, () -> register("same-key"));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
        assertThat(responses.stream().map(ConcurrencyTest::id).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remittances WHERE idempotency_key = 'same-key'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void completingTheSameRemittanceTwiceAtOnceMovesTheCashOnce() throws Exception {
        String id = assigned(courier);

        List<MockHttpServletResponse> responses = atOnce(2,
                () -> post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(courierToken)));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        assertThat(payouts(id)).isEqualTo(1);
    }

    @Test
    void theSameSyncedActionAtOnceIsAppliedOnce() throws Exception {
        String id = assigned(courier);
        String action = """
                {"actions": [{"id": "%s", "type": "DELIVER", "remittanceId": "%s", "occurredAt": "%s", "payload": {}}]}
                """.formatted(UUID.randomUUID(), id, Instant.now());

        List<MockHttpServletResponse> responses = atOnce(THREADS, () -> post("/api/v1/sync/actions")
                .header("Authorization", bearer(courierToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(action));

        List<String> statuses = responses.stream()
                .map(r -> (String) JsonPath.read(content(r), "$.results[0].status")).toList();
        assertThat(statuses).containsOnlyOnce("APPLIED");
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn("APPLIED", "ALREADY_APPLIED", "REJECTED"));
        assertThat(payouts(id)).isEqualTo(1);
    }

    @Test
    void aNewCourierCompletingTwoRemittancesAtOnceGetsOneCashAccount() throws Exception {
        User newCourier = createUser("new.courier@ottos.test", "DELIVERY");
        String token = accessToken("new.courier@ottos.test");
        List<String> ids = List.of(assigned(newCourier), assigned(newCourier));

        List<MockHttpServletResponse> responses = atOnce(ids.size(), new Supplier<>() {
            private int next;

            @Override
            public synchronized RequestBuilder get() {
                return post("/api/v1/remittances/{id}/deliver", ids.get(next++)).header("Authorization", bearer(token));
            }
        });

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(200));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_accounts WHERE owner_id = ?", Integer.class,
                newCourier.getId())).isEqualTo(1);
        String cash = content(mvc.perform(get("/api/v1/cash/me").header("Authorization", bearer(token)))
                .andReturn().getResponse());
        assertThat(((Number) JsonPath.read(cash, "$.cash.balances[0].amount")).doubleValue()).isEqualTo(-82000.0);
    }

    // ---------------------------------------------------------------- helpers

    /** Builds the requests first, then releases all threads together so they hit the server at the same time. */
    private List<MockHttpServletResponse> atOnce(int threads, Supplier<RequestBuilder> request) throws Exception {
        List<RequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            requests.add(request.get());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (RequestBuilder r : requests) {
                Callable<MockHttpServletResponse> call = () -> {
                    start.await();
                    return mvc.perform(r).andReturn().getResponse();
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> f : futures) {
                responses.add(f.get());
            }
            return Collections.unmodifiableList(responses);
        } finally {
            pool.shutdownNow();
        }
    }

    private RequestBuilder register(String idempotencyKey) {
        var request = post("/api/v1/remittances")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "DELIVERY", "customerId": "%s", "beneficiaryId": "%s", "amount": 100, "targetCurrency": "CUP"}
                        """.formatted(customerId, beneficiaryId));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return request;
    }

    private String assigned(User to) throws Exception {
        String id = id(mvc.perform(register(null)).andReturn().getResponse());
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\": \"" + to.getId() + "\"}"));
        return id;
    }

    private int payouts(String remittanceId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cash_movements WHERE remittance_id = ? AND type = 'DELIVERY_PAYOUT'",
                Integer.class, UUID.fromString(remittanceId));
    }

    private static String content(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String id(MockHttpServletResponse response) {
        return JsonPath.read(content(response), "$.id");
    }
}
