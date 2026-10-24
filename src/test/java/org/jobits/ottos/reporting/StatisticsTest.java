package org.jobits.ottos.reporting;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.jobits.ottos.reporting.MonthlyReportDetailTest.decimal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/statistics.md. */
class StatisticsTest extends ReportTestSupport {

    private static final List<String> FIGURES = List.of("remittances", "deliveries", "pickups", "deliveryAmount",
            "collected", "fees", "deliveredCurrency", "pickupCollected", "onTheHouse.remittances", "onTheHouse.amount",
            "onTheHouse.waivedFees");

    @Test
    void groupsByWeekMonthDayOfWeekAndMunicipalityInCubaTime() throws Exception {
        // Wednesday 30 September to Tuesday 6 October 2026: two cut weeks and two cut months
        String wednesday = register("DELIVERY", "100", "CUP");
        String thursday = register("DELIVERY", "37", "CUP");
        String monday = register("PICKUP", "200", "CUP");
        String tuesday = register("DELIVERY", "100", "USD", otherBeneficiaryId, ON_THE_HOUSE);
        String voided = register("DELIVERY", "500", "CUP");
        String dayBefore = register("DELIVERY", "500", "CUP");
        String dayAfter = register("DELIVERY", "500", "CUP");
        voidIt(voided, "Error");
        // 23:30 in Cuba is already 1 October in UTC: it belongs to September
        registeredAt(wednesday, LocalDateTime.of(2026, 9, 30, 23, 30));
        registeredAt(thursday, LocalDateTime.of(2026, 10, 1, 0, 0));
        registeredAt(voided, LocalDateTime.of(2026, 10, 2, 12, 0));
        registeredAt(monday, LocalDateTime.of(2026, 10, 5, 10, 0));
        registeredAt(tuesday, LocalDateTime.of(2026, 10, 6, 23, 59, 59));
        registeredAt(dayBefore, LocalDateTime.of(2026, 9, 29, 23, 59, 59));
        registeredAt(dayAfter, LocalDateTime.of(2026, 10, 7, 0, 0));

        String json = body(statistics("2026-09-30", "2026-10-06", salesToken)
                .andExpect(jsonPath("$.from").value("2026-09-30"))
                .andExpect(jsonPath("$.to").value("2026-10-06"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.businessCurrency").value("USD"))
                .andExpect(jsonPath("$.totals.remittances").value(4))
                .andExpect(jsonPath("$.totals.deliveries").value(3))
                .andExpect(jsonPath("$.totals.pickups").value(1))
                // 100 + 37 + 100 sent; 110 + 47 + 100 collected (on the house: no fee)
                .andExpect(jsonPath("$.totals.deliveryAmount").value(237.00))
                .andExpect(jsonPath("$.totals.averageAmount").value(79.00))
                .andExpect(jsonPath("$.totals.collected").value(257.00))
                .andExpect(jsonPath("$.totals.fees").value(40.00))
                .andExpect(jsonPath("$.totals.deliveredCurrency").value(236.95))
                .andExpect(jsonPath("$.totals.pickupCollected").value(200.00))
                .andExpect(jsonPath("$.totals.onTheHouse.remittances").value(1))
                .andExpect(jsonPath("$.totals.onTheHouse.amount").value(100.00))
                .andExpect(jsonPath("$.totals.onTheHouse.waivedFees").value(10.00))

                .andExpect(jsonPath("$.byWeek", hasSize(2)))
                .andExpect(jsonPath("$.byWeek[0].week").value("2026-W40"))
                .andExpect(jsonPath("$.byWeek[0].from").value("2026-09-30"))
                .andExpect(jsonPath("$.byWeek[0].to").value("2026-10-04"))
                .andExpect(jsonPath("$.byWeek[0].partial").value(true))
                .andExpect(jsonPath("$.byWeek[0].remittances").value(2))
                .andExpect(jsonPath("$.byWeek[0].deliveryAmount").value(137.00))
                .andExpect(jsonPath("$.byWeek[0].averageAmount").value(68.50))
                .andExpect(jsonPath("$.byWeek[0].onTheHouse.remittances").value(0))
                .andExpect(jsonPath("$.byWeek[1].week").value("2026-W41"))
                .andExpect(jsonPath("$.byWeek[1].from").value("2026-10-05"))
                .andExpect(jsonPath("$.byWeek[1].to").value("2026-10-06"))
                .andExpect(jsonPath("$.byWeek[1].partial").value(true))
                .andExpect(jsonPath("$.byWeek[1].remittances").value(2))
                .andExpect(jsonPath("$.byWeek[1].pickups").value(1))
                .andExpect(jsonPath("$.byWeek[1].deliveryAmount").value(100.00))
                .andExpect(jsonPath("$.byWeek[1].onTheHouse.remittances").value(1))
                .andExpect(jsonPath("$.byWeek[1].onTheHouse.amount").value(100.00))
                .andExpect(jsonPath("$.byWeek[1].onTheHouse.waivedFees").value(10.00))

                .andExpect(jsonPath("$.byMonth", hasSize(2)))
                .andExpect(jsonPath("$.byMonth[0].month").value("2026-09"))
                .andExpect(jsonPath("$.byMonth[0].from").value("2026-09-30"))
                .andExpect(jsonPath("$.byMonth[0].to").value("2026-09-30"))
                .andExpect(jsonPath("$.byMonth[0].partial").value(true))
                .andExpect(jsonPath("$.byMonth[0].remittances").value(1))
                .andExpect(jsonPath("$.byMonth[0].collected").value(110.00))
                .andExpect(jsonPath("$.byMonth[1].month").value("2026-10"))
                .andExpect(jsonPath("$.byMonth[1].from").value("2026-10-01"))
                .andExpect(jsonPath("$.byMonth[1].to").value("2026-10-06"))
                .andExpect(jsonPath("$.byMonth[1].remittances").value(3))
                .andExpect(jsonPath("$.byMonth[1].onTheHouse.remittances").value(1))

                .andExpect(jsonPath("$.byDayOfWeek", hasSize(7)))
                .andExpect(jsonPath("$.byDayOfWeek[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.byDayOfWeek[0].pickups").value(1))
                .andExpect(jsonPath("$.byDayOfWeek[1].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.byDayOfWeek[1].onTheHouse.remittances").value(1))
                .andExpect(jsonPath("$.byDayOfWeek[2].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.byDayOfWeek[2].deliveryAmount").value(100.00))
                .andExpect(jsonPath("$.byDayOfWeek[3].dayOfWeek").value("THURSDAY"))
                .andExpect(jsonPath("$.byDayOfWeek[3].deliveryAmount").value(37.00))
                .andExpect(jsonPath("$.byDayOfWeek[4].remittances").value(0))
                .andExpect(jsonPath("$.byDayOfWeek[4].averageAmount").value(0))
                .andExpect(jsonPath("$.byDayOfWeek[6].dayOfWeek").value("SUNDAY"))

                .andExpect(jsonPath("$.byMunicipality", hasSize(2)))
                .andExpect(jsonPath("$.byMunicipality[0].municipalityCode").value("2302"))
                .andExpect(jsonPath("$.byMunicipality[0].municipalityName").isNotEmpty())
                .andExpect(jsonPath("$.byMunicipality[0].remittances").value(3))
                .andExpect(jsonPath("$.byMunicipality[1].municipalityCode").value("2303"))
                .andExpect(jsonPath("$.byMunicipality[1].remittances").value(1)));

        for (String group : List.of("byWeek", "byMonth", "byDayOfWeek", "byMunicipality")) {
            assertAddsUpToTheTotals(json, group);
        }
    }

    @Test
    void aWholeMonthIsTheMonthlyReport() throws Exception {
        String delivered = register("DELIVERY", "100", "CUP");
        register("DELIVERY", "37", "CUP");
        register("DELIVERY", "100", "USD", otherBeneficiaryId, ON_THE_HOUSE);
        register("PICKUP", "200", "CUP");
        voidIt(register("DELIVERY", "60", "CUP"), "Error");
        assignAndDeliver(delivered);
        YearMonth month = YearMonth.now(clock);

        String json = body(statistics(month.atDay(1).toString(), month.atEndOfMonth().toString(), adminToken)
                .andExpect(jsonPath("$.byMonth", hasSize(1)))
                .andExpect(jsonPath("$.byMonth[0].partial").value(false)));
        String monthly = body(mvc.perform(get("/api/v1/reports/monthly").param("month", month.toString())
                .header("Authorization", bearer(adminToken))));

        assertThat(number(json, "$.totals.remittances")).isEqualByComparingTo(number(monthly, "$.remittances.total"));
        assertThat(number(json, "$.totals.deliveries")).isEqualByComparingTo(number(monthly, "$.remittances.delivery"));
        assertThat(number(json, "$.totals.pickups")).isEqualByComparingTo(number(monthly, "$.remittances.pickup"));
        assertThat(number(json, "$.totals.collected")).isEqualByComparingTo(number(monthly, "$.collected"));
        assertThat(number(json, "$.totals.fees")).isEqualByComparingTo(number(monthly, "$.fees"));
        assertThat(number(json, "$.totals.deliveredCurrency"))
                .isEqualByComparingTo(number(monthly, "$.deliveredCurrency"));
        assertThat(number(json, "$.totals.pickupCollected")).isEqualByComparingTo(number(monthly, "$.pickups.collected"));
        List<Object> sent = JsonPath.read(monthly, "$.byCurrency[*].sent");
        assertThat(number(json, "$.totals.deliveryAmount")).isEqualByComparingTo(
                sent.stream().map(MonthlyReportDetailTest::decimal).reduce(BigDecimal.ZERO, BigDecimal::add));
        for (String field : List.of("remittances", "amount", "waivedFees")) {
            assertThat(number(json, "$.totals.onTheHouse." + field))
                    .isEqualByComparingTo(number(monthly, "$.onTheHouse." + field));
        }
        assertThat(number(json, "$.byMonth[0].collected")).isEqualByComparingTo(number(monthly, "$.collected"));
        for (String group : List.of("byWeek", "byMonth", "byDayOfWeek", "byMunicipality")) {
            assertAddsUpToTheTotals(json, group);
        }
    }

    @Test
    void aWholeWeekIsNotPartialAndEmptyWeeksAndMonthsAreListed() throws Exception {
        statistics("2026-10-05", "2026-10-11", salesToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byWeek", hasSize(1)))
                .andExpect(jsonPath("$.byWeek[0].week").value("2026-W41"))
                .andExpect(jsonPath("$.byWeek[0].partial").value(false))
                .andExpect(jsonPath("$.byWeek[0].remittances").value(0))
                .andExpect(jsonPath("$.byMonth[0].partial").value(true))
                .andExpect(jsonPath("$.byMunicipality", hasSize(0)));
        // ISO weeks: 31 December 2026 is in week 53 of 2026; 1 January 2027 too
        statistics("2026-12-28", "2027-01-03", salesToken)
                .andExpect(jsonPath("$.byWeek", hasSize(1)))
                .andExpect(jsonPath("$.byWeek[0].week").value("2026-W53"))
                .andExpect(jsonPath("$.byMonth", hasSize(2)));
        statistics("2026-01-01", "2026-12-31", salesToken)
                .andExpect(jsonPath("$.byMonth", hasSize(12)))
                .andExpect(jsonPath("$.byWeek[0].week").value("2026-W01"))
                .andExpect(jsonPath("$.byWeek[0].from").value("2026-01-01"))
                .andExpect(jsonPath("$.byWeek[0].partial").value(true));
    }

    @Test
    void thePeriodIsRequiredInOrderAndAtMost366Days() throws Exception {
        statistics("2024-01-01", "2024-12-31", salesToken).andExpect(status().isOk());
        statistics("2024-01-01", "2025-01-01", salesToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        statistics("2026-10-02", "2026-10-01", salesToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        statistics("2026-10-01", "2026-10-01", salesToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.byWeek", hasSize(1)));
        statistics("2026-10", "2026-10-31", salesToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/reports/statistics").param("from", "2026-10-01")
                        .header("Authorization", bearer(salesToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onlyWhoSeesReportsSeesTheStatistics() throws Exception {
        statistics("2026-10-01", "2026-10-31", courierToken).andExpect(status().isForbidden());
        statistics("2026-10-01", "2026-10-31", adminToken).andExpect(status().isOk());
    }

    private void assertAddsUpToTheTotals(String json, String group) {
        List<Map<String, Object>> rows = JsonPath.read(json, "$." + group + "[*]");
        for (String field : FIGURES) {
            BigDecimal sum = rows.stream().map(row -> decimal(JsonPath.read(row, "$." + field)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).as(group + " " + field).isEqualByComparingTo(number(json, "$.totals." + field));
        }
    }

    private static BigDecimal number(String json, String path) {
        return decimal(JsonPath.read(json, path));
    }

    private ResultActions statistics(String from, String to, String token) throws Exception {
        return mvc.perform(get("/api/v1/reports/statistics").param("from", from).param("to", to)
                .header("Authorization", bearer(token)));
    }
}
