package org.jobits.ottos.identity.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jobits.ottos.identity.management.RoleManagementService;
import org.jobits.ottos.identity.management.Views.PermissionView;
import org.jobits.ottos.identity.management.Views.RoleView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1")
@Tag(name = "Roles and permissions")
@SecurityRequirement(name = "bearer")
class RoleAdminController {

    private final RoleManagementService roles;

    RoleAdminController(RoleManagementService roles) {
        this.roles = roles;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "Permission catalog: everything a role can be granted")
    List<PermissionView> permissions() {
        return roles.listPermissions();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "List roles with their permissions")
    List<RoleView> list() {
        return roles.list();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "Get a role")
    RoleView get(@PathVariable UUID id) {
        return roles.get(id);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Create a role")
    RoleView create(@Valid @RequestBody CreateRoleRequest request, @AuthenticationPrincipal Jwt jwt) {
        return roles.create(request.code(), request.name(), request.description(), request.permissions(), Actors.from(jwt));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Update a role's name and description")
    RoleView update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return roles.describe(id, request.name(), request.description());
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Replace a role's permissions")
    RoleView replacePermissions(@PathVariable UUID id, @Valid @RequestBody PermissionsRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return roles.replacePermissions(id, request.permissions(), Actors.from(jwt));
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Delete a role that no user holds")
    void delete(@PathVariable UUID id) {
        roles.delete(id);
    }

    record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}", message = "must be UPPER_SNAKE_CASE, 2–50 characters") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<String> permissions) {
    }

    record UpdateRoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description) {
    }

    record PermissionsRequest(@NotNull Set<String> permissions) {
    }
}
