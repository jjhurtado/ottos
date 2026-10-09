package org.jobits.ottos.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jobits.ottos.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 401 and 403 raised by Spring Security, before any controller runs, answered with the same ProblemDetail shape
 * (and {@code code}) as every other error. The bearer handlers still set status and WWW-Authenticate first.
 * <ul>
 *   <li>AUTHENTICATION_REQUIRED: no access token was sent.</li>
 *   <li>INVALID_TOKEN: the token is malformed, badly signed or expired; refresh it or log in again.</li>
 *   <li>FORBIDDEN: the token is valid but lacks the permission the endpoint checks.</li>
 * </ul>
 */
@Component
class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper json;

    SecurityProblemHandlers(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, e);
        if (e instanceof OAuth2AuthenticationException) {
            write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "The access token is invalid or expired");
        } else {
            write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "An access token is required");
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        bearerAccessDenied.handle(request, response, e);
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have permission to do this");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                       String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put(ApiException.CODE, code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
