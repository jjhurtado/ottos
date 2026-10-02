package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemittanceStatusRepository extends JpaRepository<RemittanceStatus, String> {

    List<RemittanceStatus> findAllByOrderByPosition();

    List<RemittanceStatus> findByInitialTrue();

    List<RemittanceStatus> findByFinalStatusTrue();
}
