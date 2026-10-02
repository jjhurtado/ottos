package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemittanceTransitionRepository extends JpaRepository<RemittanceTransition, RemittanceTransition.Key> {

    List<RemittanceTransition> findByKeyFromStatus(String fromStatus);
}
