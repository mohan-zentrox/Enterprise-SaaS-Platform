package com.zentrox.forge.controller;

import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.dto.UserResponse;
import com.zentrox.forge.dto.user.UserCreateRequest;
import com.zentrox.forge.dto.user.UserUpdateRequest;
import com.zentrox.forge.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FRD Section 4 - user administration within the caller's own tenant.
 *
 * No endpoint here accepts a tenant id: the tenant comes from the caller's JWT via TenantContext,
 * and every query runs through the tenant-scoped repository. See UserManagementService.
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserManagementService userManagementService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_INVITE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userManagementService.listUsers(PageRequests.of(page, size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                     @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_DEACTIVATE')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.deactivateUser(id));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.activateUser(id));
    }
}
