package org.jobits.ottos.customers.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.customers.Customers;
import org.jobits.ottos.customers.Customers.CustomerInfo;
import org.jobits.ottos.customers.domain.Customer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
@SecurityRequirement(name = "bearer")
class CustomerController {

    private final Customers customers;

    CustomerController(Customers customers) {
        this.customers = customers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customers:read')")
    @Operation(summary = "Search customers by a fragment of the name or phone (max 50); without q, the most recent")
    List<CustomerInfo> search(@RequestParam(required = false) String q) {
        return customers.search(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customers:read')")
    @Operation(summary = "Get a customer")
    CustomerInfo get(@PathVariable UUID id) {
        return customers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Register a customer; the phone must not belong to another customer")
    CustomerInfo register(@Valid @RequestBody CustomerRequest request, @AuthenticationPrincipal Jwt jwt) {
        return customers.register(request.toDetails(), UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customers:write')")
    @Operation(summary = "Update a customer")
    CustomerInfo update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request.toDetails());
    }

    record CustomerRequest(@NotBlank @Size(max = 150) String fullName,
                           @NotBlank @Size(max = 30) String phone,
                           @Email @Size(max = 255) String email,
                           @Size(max = 20) String documentType,
                           @Size(max = 50) String documentNumber,
                           @Size(max = 255) String address,
                           @Size(max = 1000) String notes) {

        Customer.Details toDetails() {
            return new Customer.Details(fullName, phone, email, documentType, documentNumber, address, notes);
        }
    }
}
