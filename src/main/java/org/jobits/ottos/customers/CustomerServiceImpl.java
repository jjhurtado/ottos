package org.jobits.ottos.customers;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.Phones;
import org.jobits.ottos.customers.domain.Customer;
import org.jobits.ottos.customers.domain.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.jobits.ottos.Texts.blankToNull;

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
    public Map<UUID, CustomerInfo> findAll(Collection<UUID> ids) {
        return customers.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, CustomerInfo::of));
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
    @Transactional(readOnly = true)
    public CustomerPage page(String text, int page, int size) {
        PageRequest request = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Customer> found;
        if (text == null || text.isBlank()) {
            found = customers.findAll(request);
        } else {
            String phone = Phones.normalize(text);
            found = phone == null
                    ? customers.pageByName(text.trim(), request)
                    : customers.pageByNameOrPhone(text.trim(), phone, request);
        }
        return new CustomerPage(found.getContent().stream().map(CustomerInfo::of).toList(), page, size,
                found.getTotalElements());
    }

    @Override
    @Transactional
    public CustomerInfo register(CustomerData data, UUID actorId) {
        Customer.Details normalized = normalize(data.toDetails());
        requireNotIdentical(normalized, null);
        Instant now = clock.instant();
        return CustomerInfo.of(customers.save(new Customer(normalized, now, actorId)));
    }

    @Override
    @Transactional
    public CustomerInfo update(UUID id, CustomerData data) {
        Customer customer = customers.findById(id).orElseThrow(CustomerServiceImpl::notFound);
        Customer.Details normalized = normalize(data.toDetails());
        requireNotIdentical(normalized, id);
        customer.update(normalized);
        return CustomerInfo.of(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerInfo> findIdentical(CustomerData data) {
        return identical(normalize(data.toDetails()), null).map(CustomerInfo::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerInfo> all() {
        return customers.findAllByOrderByFullNameAsc().stream().map(CustomerInfo::of).toList();
    }

    private void requireNotIdentical(Customer.Details normalized, UUID exceptId) {
        identical(normalized, exceptId).ifPresent(other -> {
            throw ApiException.conflict("CUSTOMER_ALREADY_REGISTERED",
                    "A customer with the same name, phone and address already exists").with("customerId", other.getId());
        });
    }

    /** Same phone, and the same name and address ignoring case and spaces (no address equals no address). */
    private Optional<Customer> identical(Customer.Details normalized, UUID exceptId) {
        return customers.findByPhone(normalized.phone()).stream()
                .filter(c -> !c.getId().equals(exceptId))
                .filter(c -> same(c.getFullName(), normalized.fullName()) && same(c.getAddress(), normalized.address()))
                .findFirst();
    }

    /** Equal ignoring case and repeated or outer spaces ("Ana  Pérez" is "ana pérez"); no value equals no value. */
    private static boolean same(String a, String b) {
        return squash(a).equalsIgnoreCase(squash(b));
    }

    private static String squash(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static Customer.Details normalize(Customer.Details d) {
        String phone = Phones.normalize(d.phone());
        if (phone == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Phone must contain digits");
        }
        return new Customer.Details(d.fullName().trim(), phone, blankToNull(d.email()), blankToNull(d.documentType()),
                blankToNull(d.documentNumber()), blankToNull(d.address()), blankToNull(d.notes()));
    }


    private static ResponseStatusException notFound() {
        return ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer not found");
    }
}
