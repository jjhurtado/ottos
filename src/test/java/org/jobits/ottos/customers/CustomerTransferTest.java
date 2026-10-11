package org.jobits.ottos.customers;

import com.jayway.jsonpath.JsonPath;
import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/customer-transfer.md. */
class CustomerTransferTest extends ApiTestSupport {

    private static final String ROSA = """
            {"ref": "B1", "fullName": "Rosa Martínez", "phone": "53 5222 3344", "street": "Calle 10",
             "houseNumber": "512", "municipality": "plaza de la revolucion"}""";

    private String adminToken;
    private String salesToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor("admin@ottos.test", "ADMIN");
        salesToken = tokenFor("sales@ottos.test", "SALES");
    }

    @Test
    void aDryRunShowsTheResultAndSavesNothing() throws Exception {
        importCustomers(true, customer("C1", "Ana Pérez", "+13055550101", "Miami", ROSA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.results[0].beneficiaries[0].status").value("CREATED"))
                .andExpect(jsonPath("$.summary.customersCreated").value(1))
                .andExpect(jsonPath("$.summary.linksCreated").value(1));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM beneficiaries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isZero();
    }

    @Test
    void anImportCreatesWhatIsNewAndReusesWhatMatches() throws Exception {
        importCustomers(false, customer("C1", "Ana Pérez", "+13055550101", "Miami", ROSA)).andExpect(status().isOk());

        importCustomers(false,
                // Identical customer and beneficiary: both exist; the link already existed
                customer("C2", "ANA PÉREZ", "+1 305 555 0101", "miami", ROSA),
                // Same person, another address: another customer, linked to the existing beneficiary
                customer("C3", "Ana Pérez", "+13055550101", "Hialeah", ROSA),
                customer("C4", "Sin teléfono", "abc", null),
                customer("C5", "Luis Gómez", "+13055550202", null,
                        """
                        {"ref": "B2", "fullName": "Sin calle", "phone": "5350000000", "municipality": "2302"}""",
                        """
                        {"ref": "B3", "fullName": "Lejos", "phone": "5350000001", "street": "A", "municipality": "Madrid"}""",
                        """
                        {"ref": "B4", "fullName": "Pedro Ruiz", "phone": "5350000002", "street": "B", "municipality": "2309"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("EXISTING"))
                .andExpect(jsonPath("$.results[0].beneficiaries[0].status").value("EXISTING"))
                .andExpect(jsonPath("$.results[0].beneficiaries[0].linked").value(false))
                .andExpect(jsonPath("$.results[1].status").value("CREATED"))
                .andExpect(jsonPath("$.results[1].beneficiaries[0].status").value("EXISTING"))
                .andExpect(jsonPath("$.results[1].beneficiaries[0].linked").value(true))
                .andExpect(jsonPath("$.results[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[2].code").value("INVALID_PHONE"))
                .andExpect(jsonPath("$.results[2].ref").value("C4"))
                .andExpect(jsonPath("$.results[3].status").value("CREATED"))
                .andExpect(jsonPath("$.results[3].beneficiaries[0].code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[3].beneficiaries[1].code").value("UNKNOWN_MUNICIPALITY"))
                .andExpect(jsonPath("$.results[3].beneficiaries[2].status").value("CREATED"))
                .andExpect(jsonPath("$.summary.customersCreated").value(2))
                .andExpect(jsonPath("$.summary.customersExisting").value(1))
                .andExpect(jsonPath("$.summary.customersRejected").value(1))
                .andExpect(jsonPath("$.summary.beneficiariesCreated").value(1))
                .andExpect(jsonPath("$.summary.beneficiariesExisting").value(2))
                .andExpect(jsonPath("$.summary.beneficiariesRejected").value(2))
                .andExpect(jsonPath("$.summary.linksCreated").value(2));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE phone = '+13055550101'", Integer.class))
                .isEqualTo(2);
        mvc.perform(get("/api/v1/audit").param("action", "CUSTOMERS_IMPORTED").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].details.customersCreated").value(2));
    }

    @Test
    void aDeletedBeneficiaryIsRejectedWithoutLosingItsCustomer() throws Exception {
        String body = importCustomers(false, customer("C1", "Ana Pérez", "+13055550101", "Miami", ROSA))
                .andReturn().getResponse().getContentAsString();
        String rosa = JsonPath.read(body, "$.results[0].beneficiaries[0].beneficiaryId");
        mvc.perform(delete("/api/v1/beneficiaries/{id}", rosa).header("Authorization", bearer(adminToken)));

        importCustomers(false, customer("C2", "Luis Gómez", "+13055550202", null, ROSA))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[0].customerId").isNotEmpty())
                .andExpect(jsonPath("$.results[0].beneficiaries[0].code").value("BENEFICIARY_INACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE full_name = 'Luis Gómez'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theExportBringsEveryCustomerWithTheirBeneficiaries() throws Exception {
        importCustomers(false,
                customer("C1", "Ana Pérez", "+13055550101", "Miami", ROSA,
                        """
                        {"fullName": "Pedro Ruiz", "phone": "5350000002", "street": "B", "municipality": "2309"}"""),
                customer("C2", "Bea López", "+13055550303", null));

        mvc.perform(get("/api/v1/customers/export").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.customers", hasSize(2)))
                .andExpect(jsonPath("$.customers[0].fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.customers[0].beneficiaries", hasSize(2)))
                .andExpect(jsonPath("$.customers[0].beneficiaries[0].fullName").value("Pedro Ruiz"))
                .andExpect(jsonPath("$.customers[0].beneficiaries[1].municipalityName").value("Plaza de la Revolución"))
                .andExpect(jsonPath("$.customers[1].beneficiaries", hasSize(0)));
        mvc.perform(get("/api/v1/audit").param("action", "CUSTOMERS_EXPORTED").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.items[0].details.customers").value(2));
    }

    @Test
    void onlyAdministratorsImportAndExportAndBatchesAreLimited() throws Exception {
        mvc.perform(get("/api/v1/customers/export").header("Authorization", bearer(salesToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customers/import")
                        .header("Authorization", bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customers\": []}"))
                .andExpect(status().isForbidden());

        String[] tooMany = Collections.nCopies(501, customer(null, "X", "+1305", null)).toArray(String[]::new);
        importCustomers(false, tooMany).andExpect(status().isBadRequest());
    }

    private ResultActions importCustomers(boolean dryRun, String... customers) throws Exception {
        return mvc.perform(post("/api/v1/customers/import")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRun\": " + dryRun + ", \"customers\": [" + String.join(",", customers) + "]}"));
    }

    private static String customer(String ref, String name, String phone, String address, String... beneficiaries) {
        return """
                {"ref": %s, "fullName": "%s", "phone": "%s", "address": %s, "beneficiaries": [%s]}
                """.formatted(ref == null ? "null" : "\"" + ref + "\"", name, phone,
                address == null ? "null" : "\"" + address + "\"", String.join(",", beneficiaries));
    }
}
