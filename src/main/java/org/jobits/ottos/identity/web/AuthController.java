package org.jobits.ottos.identity.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.identity.ClientType;
import org.jobits.ottos.identity.security.AuthenticationService;
import org.jobits.ottos.identity.security.ClientIp;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthenticationService authentication;
    private final ClientIp clientIp;

    AuthController(AuthenticationService authentication, ClientIp clientIp) {
        this.authentication = authentication;
        this.clientIp = clientIp;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password and get an access token and a refresh token; repeated failures lock the email or IP for a while (429)")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        ClientType client = request.client() == null ? ClientType.WEB : request.client();
        return TokenResponse.of(authentication.login(request.email(), request.password(), client, clientIp.of(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair; the refresh token can be used only once")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.of(authentication.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    void logout(@Valid @RequestBody RefreshRequest request) {
        authentication.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user", security = @SecurityRequirement(name = "bearer"))
    CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUserResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("type"),
                jwt.getClaimAsString("client"),
                jwt.getClaimAsStringList("roles"),
                jwt.getClaimAsStringList("permissions"));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change your own password; signs you out of every device",
            security = @SecurityRequirement(name = "bearer"))
    void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(UUID.fromString(jwt.getSubject()), request.currentPassword(), request.newPassword());
    }

    /** @param client MOBILE for the mobile app (access tokens for a whole working day); WEB, the default, otherwise */
    record LoginRequest(@NotBlank @Email String email, @NotBlank String password, ClientType client) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record ChangePasswordRequest(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn,
                         String refreshToken, long refreshExpiresIn) {

        static TokenResponse of(AuthenticationService.Session session) {
            return new TokenResponse(
                    session.accessToken().value(), "Bearer", session.accessToken().expiresInSeconds(),
                    session.refreshToken().value(), session.refreshToken().expiresInSeconds());
        }
    }

    record CurrentUserResponse(String id, String email, String name, String type, String client,
                               List<String> roles, List<String> permissions) {
    }
}
