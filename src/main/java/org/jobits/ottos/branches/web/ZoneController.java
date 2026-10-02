package org.jobits.ottos.branches.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jobits.ottos.branches.Zones;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reference data: any authenticated staff member can read it. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Zones")
@SecurityRequirement(name = "bearer")
class ZoneController {

    private final Zones zones;

    ZoneController(Zones zones) {
        this.zones = zones;
    }

    @GetMapping("/provinces")
    @Operation(summary = "Active provinces")
    List<Zones.ProvinceInfo> provinces() {
        return zones.provinces();
    }

    @GetMapping("/municipalities")
    @Operation(summary = "Active municipalities, optionally of one province")
    List<Zones.MunicipalityInfo> municipalities(@RequestParam(required = false) String province) {
        return zones.municipalities(province);
    }
}
