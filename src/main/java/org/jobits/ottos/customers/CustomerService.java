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

    /** Fails with CUSTOMER_ALREADY_REGISTERED if an identical customer (same name, phone and address) exists. */
    CustomerInfo register(CustomerData data, UUID actorId);

    CustomerInfo update(UUID id, CustomerData data);

    /**
     * The customer identical to these details (same name and address, ignoring case and outer spaces, and the same
     * normalized phone), if any. Same name and phone with another address is another customer.
     */
    Optional<CustomerInfo> findIdentical(CustomerData data);

    /** Every customer, by name; for the export. */
    List<CustomerInfo> all();

    /** A customer's data, to register or update one. */
    record CustomerData(String fullName, String phone, String email, String documentType, String documentNumber,
                        String address, String notes) {

        Customer.Details toDetails() {
            return new Customer.Details(fullName, phone, email, documentType, documentNumber, address, notes);
        }
    }

    record CustomerInfo(UUID id, String fullName, String phone, String email, String documentType,
                 String documentNumber, String address, String notes, boolean active, Instant createdAt) {

        static CustomerInfo of(Customer c) {
            return new CustomerInfo(c.getId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getDocumentType(),
                    c.getDocumentNumber(), c.getAddress(), c.getNotes(), c.isActive(), c.getCreatedAt());
        }
    }
}
