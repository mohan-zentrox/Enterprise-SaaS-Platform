package com.zentrox.forge.controller;

import com.zentrox.forge.dto.role.RoleResponse;
import com.zentrox.forge.dto.role.RoleWriteRequest;
import com.zentrox.forge.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * FRD Section 4 (RBAC) - per-tenant role administration.
 *
 * Reads are gated on USER_READ rather than ROLE_MANAGE: assigning a role requires being able to
 * see the list of roles, and USER_READ is the permission that already implies "can administer
 * people". Writes require ROLE_MANAGE. System roles are rejected for update/delete in the service.
 */
@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleManagementService roleManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleManagementService.listRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID id) {
        return ResponseEntity.ok(roleManagementService.getRole(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleManagementService.createRole(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id,
                                                    @Valid @RequestBody RoleWriteRequest request) {
        return ResponseEntity.ok(roleManagementService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleManagementService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
