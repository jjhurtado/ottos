package org.jobits.ottos.customers;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomersAndBeneficiariesTest extends ApiTestSupport {

    private String salesToken;

    @BeforeEach
    void setUp() throws Exception {
        salesToken = tokenFor("sales@ottos.test", "SALES");
    }

    @Test
    void registersACustomerWithANormalizedPhoneAndOptionalDocument() throws Exception {
        registerCustomer("Ana Pérez", "+1 (305) 555-0101")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+13055550101"))
                .andExpect(jsonPath("$.documentNumber").doesNotExist());
    }

    @Test
    void phoneIdentifiesTheCustomer() throws Exception {
        registerCustomer("Ana Pérez", "+1 305 555 0101").andExpect(status().isCreated());

        registerCustomer("Another Ana", "+1-305-555-0101").andExpect(status().isConflict());
    }

    @Test
    void searchesCustomersByNameOrPhone() throws Exception {
        registerCustomer("Ana Pérez", "+13055550101");
        registerCustomer("Luis Gómez", "+13055550202");

        mvc.perform(get("/api/v1/customers").param("q", "pérez").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Ana Pérez"));
        mvc.perform(get("/api/v1/customers").param("q", "555-0202").header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Luis Gómez"));
    }

    @Test
    void aBeneficiaryCanBelongToSeveralCustomers() throws Exception {
        String ana = id(registerCustomer("Ana Pérez", "+13055550101"));
        String luis = id(registerCustomer("Luis Gómez", "+13055550202"));

        String body = createBeneficiary(ana, "2309")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("5352223344"))
                .andExpect(jsonPath("$.municipalityName").value("Diez de Octubre"))
                .andExpect(jsonPath("$.provinceName").value("La Habana"))
                .andReturn().getResponse().getContentAsString();
        String rosa = JsonPath.read(body, "$.id");

        mvc.perform(put("/api/v1/customers/{c}/beneficiaries/{b}", luis, rosa).header("Authorization", bearer(salesToken)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/customers/{c}/beneficiaries", luis).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Rosa Martínez"));
        mvc.perform(get("/api/v1/beneficiaries/{id}", rosa).header("Authorization", bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(2)));

        mvc.perform(delete("/api/v1/customers/{c}/beneficiaries/{b}", luis, rosa).header("Authorization", bearer(salesToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/beneficiaries/{id}", rosa).header("Authorization", bearer(salesToken)))
                .andExpect(jsonPath("$.customers", hasSize(1)));
    }

    @Test
    void beneficiaryNeedsAKnownMunicipality() throws Exception {
        String ana = id(registerCustomer("Ana Pérez", "+13055550101"));

        createBeneficiary(ana, "9999").andExpect(status().isBadRequest());
    }

    @Test
    void deliveryCannotSeeCustomers() throws Exception {
        String deliveryToken = tokenFor("delivery@ottos.test", "DELIVERY");

        mvc.perform(get("/api/v1/customers").header("Authorization", bearer(deliveryToken)))
                .andExpect(status().isForbidden());
    }

    private ResultActions registerCustomer(String name, String phone) throws Exception {
        return mvc.perform(post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "%s", "phone": "%s"}
                        """.formatted(name, phone)));
    }

    private ResultActions createBeneficiary(String customerId, String municipality) throws Exception {
        return mvc.perform(post("/api/v1/customers/{c}/beneficiaries", customerId)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Rosa Martínez", "phone": "53 5222 3344", "street": "Calle 10",
                         "houseNumber": "512", "betweenStreets": "Línea y 23", "municipalityCode": "%s",
                         "reference": "Casa azul", "notes": "Llamar antes"}
                        """.formatted(municipality)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
