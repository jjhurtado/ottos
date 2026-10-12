package org.jobits.ottos.apps.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jobits.ottos.apps.AppVersionService;
import org.jobits.ottos.apps.AppVersionService.AppVersionView;
import org.jobits.ottos.apps.Platform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "App versions")
class AppVersionController {

    private final AppVersionService versions;

    AppVersionController(AppVersionService versions) {
        this.versions = versions;
    }

    @GetMapping("/version")
    @Operation(summary = "Latest and minimum build of the mobile app for a platform; public, so it works before login")
    AppVersionView version(@RequestParam Platform platform) {
        return versions.current(platform);
    }
}
