package org.jobits.ottos.customers;

import org.jobits.ottos.customers.domain.Customer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Registers and finds customers. Other modules use {@link #find(UUID)}. */
public interface CustomerService {

    Optional<CustomerInfo> find(UUID id);

    CustomerInfo get(UUID id);

    /** Matches a fragment of the name or of the phone; without text, the most recent customers. */
    List<CustomerInfo> search(String text);

    CustomerInfo register(Customer.Details details, UUID actorId);

    CustomerInfo update(UUID id, Customer.Details details);

    record CustomerInfo(UUID id, String fullName, String phone, String email, String documentType,
                 String documentNumber, String address, String notes, boolean active, Instant createdAt) {

        static CustomerInfo of(Customer c) {
            return new CustomerInfo(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getDocumentType(),
                    c.getDocumentNumber(), c.getAddress(), c.getNotes(), c.isActive(), c.getCreatedAt());
        }
    }
}
