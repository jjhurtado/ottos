package org.jobits.ottos.customers;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.Phones;
import org.jobits.ottos.customers.domain.Customer;
import org.jobits.ottos.customers.domain.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation of {@link CustomerService}. */
@Service
class CustomerServiceImpl implements CustomerService {

    private static final int SEARCH_LIMIT = 50;

    private final CustomerRepository customers;
    private final Clock clock;

    CustomerServiceImpl(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerInfo> find(UUID id) {
        return customers.findById(id).map(CustomerInfo::of);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerInfo get(UUID id) {
        return find(id).orElseThrow(CustomerServiceImpl::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerInfo> search(String text) {
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT);
        List<Customer> found;
        if (text == null || text.isBlank()) {
            found = customers.findAllByOrderByCreatedAtDesc(page);
        } else {
            String phone = Phones.normalize(text);
            found = phone == null
                    ? customers.searchByName(text.trim(), page)
                    : customers.searchByNameOrPhone(text.trim(), phone, page);
        }
        return found.stream().map(CustomerInfo::of).toList();
    }

    @Override
    @Transactional
    public CustomerInfo register(Customer.Details details, UUID actorId) {
        Customer.Details normalized = normalize(details);
        requirePhoneAvailable(normalized.phone(), null);
        Instant now = clock.instant();
        return CustomerInfo.of(customers.save(new Customer(normalized, now, actorId)));
    }

    @Override
    @Transactional
    public CustomerInfo update(UUID id, Customer.Details details) {
        Customer customer = customers.findById(id).orElseThrow(CustomerServiceImpl::notFound);
        Customer.Details normalized = normalize(details);
        requirePhoneAvailable(normalized.phone(), id);
        customer.update(normalized);
        return CustomerInfo.of(customer);
    }

    private void requirePhoneAvailable(String phone, UUID exceptId) {
        customers.findByPhone(phone)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> {
                    throw ApiException.conflict("PHONE_ALREADY_REGISTERED",
                            "Phone " + phone + " already belongs to customer " + other.getId());
                });
    }

    private static Customer.Details normalize(Customer.Details d) {
        String phone = Phones.normalize(d.phone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone must contain digits");
        }
        return new Customer.Details(d.fullName().trim(), phone, blankToNull(d.email()), blankToNull(d.documentType()),
                blankToNull(d.documentNumber()), blankToNull(d.address()), blankToNull(d.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound() {
        return ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer not found");
    }
}
