package org.jobits.ottos.identity.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.identity.management.UserManagementService;
import org.jobits.ottos.identity.management.Views.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Staff users")
@SecurityRequirement(name = "bearer")
class UserAdminController {

    private final UserManagementService users;

    UserAdminController(UserManagementService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "List staff users")
    List<UserView> list() {
        return users.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Get a staff user")
    UserView get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Create a staff user with an initial password and roles")
    UserView create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return users.create(request.email(), request.name(), request.password(), request.roleIds(), Actors.from(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Update a staff user's details")
    UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.rename(id, request.name());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Replace a staff user's roles")
    UserView replaceRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest request, @AuthenticationPrincipal Jwt jwt) {
        return users.replaceRoles(id, request.roleIds(), Actors.from(jwt));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Deactivate a staff user and revoke their sessions")
    UserView deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return users.deactivate(id, Actors.from(jwt));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Reactivate a staff user")
    UserView activate(@PathVariable UUID id) {
        return users.activate(id);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Set a new password for a staff user and revoke their sessions")
    void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest request) {
        users.resetPassword(id, request.password());
    }

    record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email,
                             @NotBlank @Size(max = 150) String name,
                             @NotBlank @Size(min = 8, max = 72) String password,
                             @NotNull Set<UUID> roleIds) {
    }

    record UpdateUserRequest(@NotBlank @Size(max = 150) String name) {
    }

    record RolesRequest(@NotNull Set<UUID> roleIds) {
    }

    record PasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {
    }
}
