package org.jobits.ottos.reporting;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/monthly-report.md, section "Detalle". */
class MonthlyReportDetailTest extends ReportTestSupport {

    /** The fields of a line: nothing else, and nothing personal. */
    private static final Set<String> LINE_FIELDS = Set.of("id", "code", "type", "status", "statusName", "createdAt",
            "expectedDate", "completedAt", "completedFromBusiness", "municipalityCode", "municipalityName",
            "courierName", "sourceCurrency", "targetCurrency", "amount", "fee", "total", "rate", "amountToDeliver",
            "collected", "deliveredCurrency", "onTheHouse", "waivedFee", "withIncident", "voided");

    @Test
    void everyRemittanceOfTheMonthWithoutPersonalDataAddingUpToTheMonthlyReport() throws Exception {
        String delivered = register("DELIVERY", "100", "CUP");
        String onTheHouse = register("DELIVERY", "100", "USD", otherBeneficiaryId, ON_THE_HOUSE);
        String pickup = register("PICKUP", "200", "CUP");
        String voided = register("DELIVERY", "37", "CUP");
        assignAndDeliver(delivered);
        voidIt(voided, "Registrada dos veces");
        YearMonth month = YearMonth.now(clock);
        LocalDateTime first = month.atDay(1).atTime(8, 0);
        // Out of registration order, so the list has to sort them; the first two at the same instant go by code
        registeredAt(pickup, first);
        registeredAt(voided, first);
        registeredAt(onTheHouse, first.plusHours(1));
        registeredAt(delivered, first.plusHours(2));

        String json = body(detail(month.toString(), salesToken)
                .andExpect(jsonPath("$.month").value(month.toString()))
                .andExpect(jsonPath("$.from").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.to").value(month.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.businessCurrency").value("USD"))
                .andExpect(jsonPath("$.counts.lines").value(4))
                .andExpect(jsonPath("$.counts.voided").value(1))
                .andExpect(jsonPath("$.counts.total").value(3))
                .andExpect(jsonPath("$.counts.delivery").value(2))
                .andExpect(jsonPath("$.counts.pickup").value(1))
                .andExpect(jsonPath("$.counts.completed").value(1))
                .andExpect(jsonPath("$.counts.open").value(2))
                .andExpect(jsonPath("$.counts.withIncident").value(0))
                .andExpect(jsonPath("$.remittances", hasSize(4))));

        List<String> ids = JsonPath.read(json, "$.remittances[*].id");
        boolean pickupFirst = code(pickup).compareTo(code(voided)) < 0;
        assertThat(ids).containsExactly(pickupFirst ? pickup : voided, pickupFirst ? voided : pickup, onTheHouse,
                delivered);

        List<Map<String, Object>> lines = JsonPath.read(json, "$.remittances[*]");
        for (Map<String, Object> line : lines) {
            assertThat(line.keySet()).isEqualTo(LINE_FIELDS);
        }
        // No personal data anywhere in the answer
        assertThat(json).doesNotContain("Ana", "Pérez", "Rosa", "Luis", "3055550101", "5352223344", "5352225566",
                "Calle Secreta", "Casa azul", "Nota privada", "Cumpleaños", "customer", "beneficiary", "pin",
                "notes", "createdBy", "Reason\"");

        Map<String, Object> deliveredLine = line(lines, delivered);
        assertThat(deliveredLine.get("type")).isEqualTo("DELIVERY");
        assertThat(deliveredLine.get("status")).isEqualTo("DELIVERED");
        assertThat(deliveredLine.get("statusName")).isNotNull();
        assertThat(deliveredLine.get("municipalityCode")).isEqualTo("2302");
        assertThat(deliveredLine.get("municipalityName")).isNotNull();
        assertThat(deliveredLine.get("courierName")).isEqualTo("User courier@ottos.test");
        assertThat(deliveredLine.get("completedAt")).isNotNull();
        assertThat(decimal(deliveredLine.get("collected"))).isEqualByComparingTo("110.00");
        assertThat(decimal(deliveredLine.get("deliveredCurrency"))).isEqualByComparingTo("100.00");
        assertThat(decimal(deliveredLine.get("amountToDeliver"))).isEqualByComparingTo("41000");
        assertThat(deliveredLine.get("voided")).isNull();

        Map<String, Object> onTheHouseLine = line(lines, onTheHouse);
        assertThat(onTheHouseLine.get("onTheHouse")).isEqualTo(true);
        assertThat(onTheHouseLine.get("municipalityCode")).isEqualTo("2303");
        assertThat(decimal(onTheHouseLine.get("fee"))).isEqualByComparingTo("0");
        assertThat(decimal(onTheHouseLine.get("waivedFee"))).isPositive();
        assertThat(onTheHouseLine.get("courierName")).isNull();

        Map<String, Object> pickupLine = line(lines, pickup);
        assertThat(decimal(pickupLine.get("collected"))).isEqualByComparingTo("200.00");
        assertThat(pickupLine.get("deliveredCurrency")).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> voidedInfo = (Map<String, Object>) line(lines, voided).get("voided");
        assertThat(voidedInfo.get("at")).isNotNull();
        assertThat(voidedInfo.get("by")).isNotNull();
        assertThat(voidedInfo.get("byName")).isEqualTo("User admin@ottos.test");
        assertThat(voidedInfo.get("reason")).isEqualTo("Registrada dos veces");

        // The lines that are not voided add up to the monthly report
        String monthly = body(mvc.perform(get("/api/v1/reports/monthly").param("month", month.toString())
                .header("Authorization", bearer(salesToken))));
        List<Map<String, Object>> counted = lines.stream().filter(l -> l.get("voided") == null).toList();
        List<Map<String, Object>> deliveries = counted.stream().filter(l -> "DELIVERY".equals(l.get("type"))).toList();
        List<Map<String, Object>> pickups = counted.stream().filter(l -> "PICKUP".equals(l.get("type"))).toList();
        assertThat(sum(deliveries, "collected")).isEqualByComparingTo(read(monthly, "$.collected"));
        assertThat(sum(counted, "fee")).isEqualByComparingTo(read(monthly, "$.fees"));
        assertThat(sum(deliveries, "deliveredCurrency")).isEqualByComparingTo(read(monthly, "$.deliveredCurrency"));
        assertThat(sum(pickups, "collected")).isEqualByComparingTo(read(monthly, "$.pickups.collected"));
        assertThat(sum(deliveries.stream().filter(l -> Boolean.TRUE.equals(l.get("onTheHouse"))).toList(), "waivedFee"))
                .isEqualByComparingTo(read(monthly, "$.onTheHouse.waivedFees"));
        assertThat((Integer) JsonPath.read(monthly, "$.remittances.total")).isEqualTo(counted.size());
    }

    @Test
    void theMonthGoesFromMidnightToMidnightInCubaTime() throws Exception {
        YearMonth month = YearMonth.of(2026, 9);
        String before = register("DELIVERY", "100", "CUP");
        String firstMoment = register("DELIVERY", "100", "CUP");
        String lastMoment = register("DELIVERY", "100", "CUP");
        String after = register("DELIVERY", "100", "CUP");
        registeredAt(before, month.atDay(1).atStartOfDay().minusSeconds(1));
        registeredAt(firstMoment, month.atDay(1).atStartOfDay());
        registeredAt(lastMoment, month.atEndOfMonth().atTime(23, 59, 59));
        registeredAt(after, month.plusMonths(1).atDay(1).atStartOfDay());

        String json = body(detail("2026-09", adminToken).andExpect(jsonPath("$.counts.lines").value(2)));
        assertThat(JsonPath.<List<String>>read(json, "$.remittances[*].id")).containsExactly(firstMoment, lastMoment);
    }

    @Test
    void aMonthWithoutRemittancesHasAnEmptyList() throws Exception {
        detail("2020-01", salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.lines").value(0))
                .andExpect(jsonPath("$.counts.total").value(0))
                .andExpect(jsonPath("$.remittances", hasSize(0)));
    }

    @Test
    void theMonthIsRequiredAndOnlyWhoSeesReportsSeesTheDetail() throws Exception {
        detail("2026-13", salesToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/reports/monthly/remittances").header("Authorization", bearer(salesToken)))
                .andExpect(status().isBadRequest());
        detail(YearMonth.now(clock).toString(), courierToken).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/monthly/remittances").param("month", "2026-10"))
                .andExpect(status().isUnauthorized());
    }

    private String code(String id) throws Exception {
        return JsonPath.read(body(mvc.perform(get("/api/v1/remittances/{id}", id)
                .header("Authorization", bearer(adminToken)))), "$.code");
    }

    private ResultActions detail(String month, String token) throws Exception {
        return mvc.perform(get("/api/v1/reports/monthly/remittances").param("month", month)
                .header("Authorization", bearer(token)));
    }

    private static Map<String, Object> line(List<Map<String, Object>> lines, String id) {
        return lines.stream().filter(l -> id.equals(l.get("id"))).findFirst().orElseThrow();
    }

    private static BigDecimal sum(List<Map<String, Object>> lines, String field) {
        return lines.stream().map(l -> l.get(field)).filter(Objects::nonNull).map(MonthlyReportDetailTest::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal read(String json, String path) {
        return decimal(JsonPath.read(json, path));
    }

    static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
