package org.jobits.ottos.beneficiaries.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerBeneficiaryRepository extends JpaRepository<CustomerBeneficiary, CustomerBeneficiary.Key> {

    List<CustomerBeneficiary> findByKeyBeneficiaryId(UUID beneficiaryId);
}
