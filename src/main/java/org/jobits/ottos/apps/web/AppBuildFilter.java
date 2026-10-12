package org.jobits.ottos.apps.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.apps.AppVersionService;
import org.jobits.ottos.apps.AppVersionService.AppVersionView;
import org.jobits.ottos.apps.Platform;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns away mobile builds below the minimum with 426 APP_UPDATE_REQUIRED, so an outdated app cannot work against a
 * contract it no longer understands. Applies only to requests carrying X-App-Platform and X-App-Build (the mobile
 * app; the web panel sends neither). Logging in, refreshing and sending the offline queue stay allowed, so a courier
 * can upload the deliveries already made before updating.
 */
@Component
class AppBuildFilter extends OncePerRequestFilter {

    static final String PLATFORM_HEADER = "X-App-Platform";
    static final String BUILD_HEADER = "X-App-Build";
    private static final Set<String> ALWAYS_ALLOWED = Set.of(
            "/api/v1/app/version", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/sync/actions");

    private final AppVersionService versions;
    private final ObjectMapper json;

    AppBuildFilter(AppVersionService versions, ObjectMapper json) {
        this.versions = versions;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(PLATFORM_HEADER) == null || request.getHeader(BUILD_HEADER) == null
                || ALWAYS_ALLOWED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Platform platform = platform(request.getHeader(PLATFORM_HEADER));
        Integer build = build(request.getHeader(BUILD_HEADER));
        if (platform != null && build != null) {
            AppVersionView version = versions.current(platform);
            if (build < version.minimumBuild()) {
                reject(request, response, version);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, AppVersionView version)
            throws IOException {
        HttpStatus status = HttpStatus.UPGRADE_REQUIRED;
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", "This version of the app is no longer supported; update to " + version.latestVersion());
        problem.put("instance", request.getRequestURI());
        problem.put(ApiException.CODE, "APP_UPDATE_REQUIRED");
        problem.put("minimumBuild", version.minimumBuild());
        problem.put("latestBuild", version.latestBuild());
        problem.put("latestVersion", version.latestVersion());
        problem.put("downloadUrl", version.downloadUrl());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    /** An unknown platform or a malformed build is ignored: the request goes on as if the headers were absent. */
    private static Platform platform(String value) {
        try {
            return Platform.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer build(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
