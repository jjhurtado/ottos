package org.jobits.ottos.reporting;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.jobits.ottos.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Setup shared by the report detail and statistics tests: rate 410 for USD-CUP, one customer, two beneficiaries. */
abstract class ReportTestSupport extends ApiTestSupport {

    protected static final String ON_THE_HOUSE = ", \"onTheHouse\": true, \"onTheHouseReason\": \"Cumpleaños de la abuela\"";

    protected String adminToken;
    protected String salesToken;
    protected String courierToken;
    protected User courier;
    protected String customerId;
    /** In municipality 2302. */
    protected String beneficiaryId;
    /** In municipality 2303. */
    protected String otherBeneficiaryId;

    @BeforeEach
    void setUpReportData() throws Exception {
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
                .content("{\"fullName\": \"Ana Pérez\", \"phone\": \"+13055550101\"}")));
        beneficiaryId = beneficiary("Rosa Martínez", "5352223344", "2302");
        otherBeneficiaryId = beneficiary("Luis Gómez", "5352225566", "2303");
    }

    private String beneficiary(String name, String phone, String municipality) throws Exception {
        return id(mvc.perform(post("/api/v1/customers/{c}/beneficiaries", customerId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "%s", "phone": "%s", "street": "Calle Secreta 10", "reference": "Casa azul",
                         "municipalityCode": "%s"}
                        """.formatted(name, phone, municipality))));
    }

    /** Registers as admin (who may register on the house); extra is appended to the JSON body. */
    protected String register(String type, String amount, String currency, String beneficiary, String extra)
            throws Exception {
        return id(mvc.perform(post("/api/v1/remittances")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "customerId": "%s", "beneficiaryId": "%s", "amount": %s, "targetCurrency": "%s",
                         "notes": "Nota privada"%s}
                        """.formatted(type, customerId, beneficiary, amount, currency, extra)))
                .andExpect(status().isCreated()));
    }

    protected String register(String type, String amount, String currency) throws Exception {
        return register(type, amount, currency, beneficiaryId, "");
    }

    protected void assignAndDeliver(String id) throws Exception {
        mvc.perform(post("/api/v1/remittances/{id}/assign", id)
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\": \"" + courier.getId() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/remittances/{id}/deliver", id).header("Authorization", bearer(courierToken)))
                .andExpect(status().isOk());
    }

    protected void voidIt(String id, String reason) throws Exception {
        mvc.perform(post("/api/v1/remittances/{id}/void", id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    /** Moves the registration to that moment, business time (Cuba). */
    protected void registeredAt(String id, LocalDateTime cubaTime) {
        jdbc.update("UPDATE remittances.remittances SET created_at = ? WHERE id = ?",
                Timestamp.from(cubaTime.atZone(clock.getZone()).toInstant()), UUID.fromString(id));
    }

    protected static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    protected static String body(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
