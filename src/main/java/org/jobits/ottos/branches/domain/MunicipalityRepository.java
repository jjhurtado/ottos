package org.jobits.ottos.branches.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MunicipalityRepository extends JpaRepository<Municipality, String> {

    @EntityGraph(attributePaths = "province")
    List<Municipality> findByActiveTrueOrderByName();

    @EntityGraph(attributePaths = "province")
    List<Municipality> findByProvinceCodeAndActiveTrueOrderByName(String provinceCode);

    @EntityGraph(attributePaths = "province")
    Optional<Municipality> findWithProvinceByCode(String code);
}
