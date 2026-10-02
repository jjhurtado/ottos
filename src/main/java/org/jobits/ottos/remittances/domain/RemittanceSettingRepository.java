package org.jobits.ottos.remittances.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemittanceSettingRepository extends JpaRepository<RemittanceSetting, String> {
}
