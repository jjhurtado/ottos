package org.jobits.ottos.branches;

import org.jobits.ottos.branches.domain.Municipality;
import org.jobits.ottos.branches.domain.MunicipalityRepository;
import org.jobits.ottos.branches.domain.ProvinceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Implementation of {@link ZoneService}. */
@Service
class ZoneServiceImpl implements ZoneService {

    private final ProvinceRepository provinces;
    private final MunicipalityRepository municipalities;

    ZoneServiceImpl(ProvinceRepository provinces, MunicipalityRepository municipalities) {
        this.provinces = provinces;
        this.municipalities = municipalities;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MunicipalityInfo> findMunicipality(String code) {
        return municipalities.findWithProvinceByCode(code)
                .filter(Municipality::isActive)
                .map(MunicipalityInfo::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceInfo> provinces() {
        return provinces.findByActiveTrueOrderByName().stream()
                .map(p -> new ProvinceInfo(p.getCode(), p.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MunicipalityInfo> municipalities(String provinceCode) {
        List<Municipality> found = provinceCode == null
                ? municipalities.findByActiveTrueOrderByName()
                : municipalities.findByProvinceCodeAndActiveTrueOrderByName(provinceCode);
        return found.stream().map(MunicipalityInfo::of).toList();
    }
}
