package org.jobits.ottos.branches.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, String> {

    List<Province> findByActiveTrueOrderByName();
}
