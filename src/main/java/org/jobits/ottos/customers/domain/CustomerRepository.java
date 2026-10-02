package org.jobits.ottos.customers.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhone(String phone);

    @Query("select c from Customer c where lower(c.fullName) like lower(concat('%', :name, '%')) order by c.fullName")
    List<Customer> searchByName(String name, Pageable page);

    @Query("""
            select c from Customer c
            where lower(c.fullName) like lower(concat('%', :name, '%')) or c.phone like concat('%', :phone, '%')
            order by c.fullName""")
    List<Customer> searchByNameOrPhone(String name, String phone, Pageable page);

    List<Customer> findAllByOrderByCreatedAtDesc(Pageable page);
}
