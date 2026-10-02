package org.jobits.ottos.beneficiaries.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @Query("select b from Beneficiary b where lower(b.fullName) like lower(concat('%', :name, '%')) order by b.fullName")
    List<Beneficiary> searchByName(String name, Pageable page);

    @Query("""
            select b from Beneficiary b
            where lower(b.fullName) like lower(concat('%', :name, '%'))
               or b.phone like concat('%', :phone, '%') or b.alternatePhone like concat('%', :phone, '%')
            order by b.fullName""")
    List<Beneficiary> searchByNameOrPhone(String name, String phone, Pageable page);

    @Query("""
            select b from Beneficiary b, CustomerBeneficiary cb
            where cb.key.beneficiaryId = b.id and cb.key.customerId = :customerId
            order by b.fullName""")
    List<Beneficiary> findByCustomer(UUID customerId);

    List<Beneficiary> findAllByOrderByCreatedAtDesc(Pageable page);
}
