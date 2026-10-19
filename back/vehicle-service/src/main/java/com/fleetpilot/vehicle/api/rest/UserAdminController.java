package com.fleetpilot.vehicle.api.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.UserAdminService;
import com.fleetpilot.vehicle.application.UserAdminService.CreatedUser;
import com.fleetpilot.vehicle.application.UserAdminService.SaveUser;
import com.fleetpilot.vehicle.application.UserAdminService.UserCriteria;
import com.fleetpilot.vehicle.application.UserAdminService.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
@PreAuthorize(Roles.ANY_STAFF)
@Tag(name = "User accounts", description = "Accounts of the client managers, drivers, garages and lessors (Keycloak). "
        + "Viewing for the FleetPilot staff, changes for the administrators.")
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Search the user accounts")
    public PageResponse<UserView> search(
            @Parameter(description = "Name, login or e-mail") @RequestParam(required = false) String q,
            @Parameter(description = "platform-admin, fleet-manager, client-manager, driver, garage, api-supplier")
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(users.search(new UserCriteria(q, role, companyId, enabled), PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "A user account")
    public UserView get(@PathVariable String id) {
        return users.get(id);
    }

    @GetMapping("/lessors")
    @Operation(summary = "Leasing companies a lessor account can be attached to")
    public List<String> lessors() {
        return users.lessors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Create an account", description = "Returns a temporary password, shown once: the user "
            + "chooses their own at the first sign-in.")
    public CreatedUser create(@Valid @RequestBody SaveUser cmd) {
        return users.create(cmd);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Update an account: details, role, attachment, enabled or disabled")
    public UserView update(@PathVariable String id, @Valid @RequestBody SaveUser cmd) {
        return users.update(id, cmd);
    }

    @PostMapping("/{id}/password")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Give a new temporary password", description = "The user must change it at the next sign-in.")
    public Map<String, String> resetPassword(@PathVariable String id) {
        return Map.of("temporaryPassword", users.resetPassword(id));
    }
}
