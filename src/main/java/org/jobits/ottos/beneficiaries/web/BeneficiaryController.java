package org.jobits.ottos.beneficiaries.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.beneficiaries.Beneficiaries;
import org.jobits.ottos.beneficiaries.Beneficiaries.BeneficiaryDetail;
import org.jobits.ottos.beneficiaries.Beneficiaries.BeneficiaryInfo;
import org.jobits.ottos.beneficiaries.domain.Beneficiary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Beneficiaries")
@SecurityRequirement(name = "bearer")
class BeneficiaryController {

    private final Beneficiaries beneficiaries;

    BeneficiaryController(Beneficiaries beneficiaries) {
        this.beneficiaries = beneficiaries;
    }

    @GetMapping("/beneficiaries")
    @PreAuthorize("hasAuthority('customers:read')")
    @Operation(summary = "Search beneficiaries by a fragment of the name or phone (max 50); without q, the most recent")
    List<BeneficiaryInfo> search(@RequestParam(required = false) String q) {
        return beneficiaries.search(q);
    }

    @GetMapping("/beneficiaries/{id}")
    @PreAuthorize("hasAuthority('customers:read')")
    @Operation(summary = "Get a beneficiary and the customers that send to them")
    BeneficiaryDetail get(@PathVariable UUID id) {
        return beneficiaries.get(id);
    }

    @PutMapping("/beneficiaries/{id}")
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Update a beneficiary")
    BeneficiaryInfo update(@PathVariable UUID id, @Valid @RequestBody BeneficiaryRequest request) {
        return beneficiaries.update(id, request.toDetails());
    }

    @GetMapping("/customers/{customerId}/beneficiaries")
    @PreAuthorize("hasAuthority('customers:read')")
    @Operation(summary = "Beneficiaries a customer sends to")
    List<BeneficiaryInfo> ofCustomer(@PathVariable UUID customerId) {
        return beneficiaries.ofCustomer(customerId);
    }

    @PostMapping("/customers/{customerId}/beneficiaries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Create a beneficiary for a customer")
    BeneficiaryInfo create(@PathVariable UUID customerId, @Valid @RequestBody BeneficiaryRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return beneficiaries.create(customerId, request.toDetails(), UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/customers/{customerId}/beneficiaries/{beneficiaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Link an existing beneficiary to a customer")
    void link(@PathVariable UUID customerId, @PathVariable UUID beneficiaryId) {
        beneficiaries.link(customerId, beneficiaryId);
    }

    @DeleteMapping("/customers/{customerId}/beneficiaries/{beneficiaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Unlink a beneficiary from a customer; past remittances are not affected")
    void unlink(@PathVariable UUID customerId, @PathVariable UUID beneficiaryId) {
        beneficiaries.unlink(customerId, beneficiaryId);
    }

    record BeneficiaryRequest(@NotBlank @Size(max = 150) String fullName,
                              @NotBlank @Size(max = 30) String phone,
                              @Size(max = 30) String alternatePhone,
                              @NotBlank @Size(max = 150) String street,
                              @Size(max = 30) String houseNumber,
                              @Size(max = 150) String betweenStreets,
                              @Size(max = 100) String neighborhood,
                              @NotBlank @Size(max = 4) String municipalityCode,
                              @Size(max = 255) String reference,
                              @Size(max = 50) String documentNumber,
                              @Size(max = 1000) String notes) {

        Beneficiary.Details toDetails() {
            return new Beneficiary.Details(fullName, phone, alternatePhone, street, houseNumber, betweenStreets,
                    neighborhood, municipalityCode, reference, documentNumber, notes);
        }
    }
}
