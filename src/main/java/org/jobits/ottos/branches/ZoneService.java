package org.jobits.ottos.branches;

import org.jobits.ottos.branches.domain.Municipality;

import java.util.List;
import java.util.Optional;

/** Provinces and municipalities. Other modules reference a municipality by its official code. */
public interface ZoneService {

    /** An active municipality, or empty if the code is unknown or inactive. */
    Optional<MunicipalityInfo> findMunicipality(String code);

    List<ProvinceInfo> provinces();

    List<MunicipalityInfo> municipalities(String provinceCode);

    record ProvinceInfo(String code, String name) {
    }

    record MunicipalityInfo(String code, String name, String provinceCode, String provinceName) {

        static MunicipalityInfo of(Municipality m) {
            return new MunicipalityInfo(m.getCode(), m.getName(), m.getProvince().getCode(), m.getProvince().getName());
        }
    }
}
