package org.jobits.ottos.branches;

import org.jobits.ottos.branches.domain.Municipality;
import org.jobits.ottos.branches.domain.MunicipalityRepository;
import org.jobits.ottos.branches.domain.ProvinceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Provinces and municipalities. Other modules reference a municipality by its official code. */
@Service
public class Zones {

    private final ProvinceRepository provinces;
    private final MunicipalityRepository municipalities;

    Zones(ProvinceRepository provinces, MunicipalityRepository municipalities) {
        this.provinces = provinces;
        this.municipalities = municipalities;
    }

    /** An active municipality, or empty if the code is unknown or inactive. */
    @Transactional(readOnly = true)
    public Optional<MunicipalityInfo> findMunicipality(String code) {
        return municipalities.findWithProvinceByCode(code)
                .filter(Municipality::isActive)
                .map(MunicipalityInfo::of);
    }

    @Transactional(readOnly = true)
    public List<ProvinceInfo> provinces() {
        return provinces.findByActiveTrueOrderByName().stream()
                .map(p -> new ProvinceInfo(p.getCode(), p.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MunicipalityInfo> municipalities(String provinceCode) {
        List<Municipality> found = provinceCode == null
                ? municipalities.findByActiveTrueOrderByName()
                : municipalities.findByProvinceCodeAndActiveTrueOrderByName(provinceCode);
        return found.stream().map(MunicipalityInfo::of).toList();
    }

    public record ProvinceInfo(String code, String name) {
    }

    public record MunicipalityInfo(String code, String name, String provinceCode, String provinceName) {

        static MunicipalityInfo of(Municipality m) {
            return new MunicipalityInfo(m.getCode(), m.getName(), m.getProvince().getCode(), m.getProvince().getName());
        }
    }
}
