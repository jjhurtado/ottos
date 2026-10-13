package org.jobits.ottos.apps;

import org.jobits.ottos.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract: docs/contracts/app-version.md. */
class AppVersionTest extends ApiTestSupport {

    private String courierToken;

    @BeforeEach
    void setUp() throws Exception {
        createUser("courier@ottos.test", "DELIVERY");
        courierToken = accessToken("courier@ottos.test");
        release("ANDROID", 12, "1.0.0-rc.1", false);
        release("ANDROID", 14, "1.0.0-rc.2", true);
        release("ANDROID", 15, "1.0.0-rc.3", false);
        release("IOS", 7, "1.0.0-rc.2", false);
        appVersions.reload();
    }

    @Test
    void anyoneCanAskWhichBuildToRunEvenBeforeLoggingIn() throws Exception {
        mvc.perform(get("/api/v1/app/version").param("platform", "ANDROID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestBuild").value(15))
                .andExpect(jsonPath("$.latestVersion").value("1.0.0-rc.3"))
                .andExpect(jsonPath("$.minimumBuild").value(14))
                .andExpect(jsonPath("$.notes").value("Notes of 1.0.0-rc.3"));
        mvc.perform(get("/api/v1/app/version").param("platform", "IOS"))
                .andExpect(jsonPath("$.latestBuild").value(7))
                .andExpect(jsonPath("$.minimumBuild").value(0));
        mvc.perform(get("/api/v1/app/version").param("platform", "WINDOWS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPlatformWithoutReleasesAsksForNothing() throws Exception {
        jdbc.update("DELETE FROM apps.app_releases WHERE platform = 'IOS'");
        appVersions.reload();

        mvc.perform(get("/api/v1/app/version").param("platform", "IOS"))
                .andExpect(jsonPath("$.latestBuild").value(0))
                .andExpect(jsonPath("$.latestVersion").doesNotExist())
                .andExpect(jsonPath("$.minimumBuild").value(0));
    }

    @Test
    void buildsBelowTheMinimumMustUpdateButCanStillUploadTheirQueue() throws Exception {
        fromApp("12", get("/api/v1/remittances/assigned"))
                .andExpect(status().is(426))
                .andExpect(jsonPath("$.code").value("APP_UPDATE_REQUIRED"))
                .andExpect(jsonPath("$.minimumBuild").value(14))
                .andExpect(jsonPath("$.latestVersion").value("1.0.0-rc.3"));
        // The deliveries already made offline are uploaded before updating
        fromApp("12", post("/api/v1/sync/actions").contentType("application/json")
                .content("{\"deviceId\": \"phone-1\", \"actions\": []}"))
                .andExpect(status().isOk());

        fromApp("14", get("/api/v1/remittances/assigned")).andExpect(status().isOk());
        // Without the headers (the web panel) nothing is checked
        mvc.perform(get("/api/v1/remittances/assigned").header("Authorization", bearer(courierToken)))
                .andExpect(status().isOk());
    }

    private ResultActions fromApp(String build,
                                  MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header("Authorization", bearer(courierToken))
                .header("X-App-Platform", "ANDROID").header("X-App-Build", build));
    }

    private void release(String platform, int build, String version, boolean mandatory) {
        jdbc.update("""
                INSERT INTO apps.app_releases (id, platform, build, version_name, mandatory, notes, released_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)""",
                platform, build, version, mandatory, "Notes of " + version);
    }
}
