package com.zentrox.forge.controller;

import com.zentrox.forge.service.RoleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The permission catalog, so a role-editing UI can render the available grants instead of
 * hardcoding a copy of the enum (which is exactly what the frontend Roles page had to do).
 * Gated on USER_READ - the same permission that lets a caller see roles.
 */
@RestController
@RequestMapping("/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final RoleManagementService roleManagementService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<String>> listPermissions() {
        return ResponseEntity.ok(roleManagementService.listPermissionCatalog());
    }
}
