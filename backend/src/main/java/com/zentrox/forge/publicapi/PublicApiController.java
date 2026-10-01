package com.zentrox.forge.publicapi;

import com.zentrox.forge.dto.workflow.WorkflowDefinitionResponse;
import com.zentrox.forge.dto.workflow.WorkflowInstanceResponse;
import com.zentrox.forge.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FRD-12.5 - the public, API-key-authenticated surface.
 *
 * <p><b>Deliberately narrow and read-only.</b> The temptation is to re-expose the whole {@code /v1}
 * surface under a second auth scheme; that doubles the attack surface and means every future
 * authenticated endpoint silently becomes public. This exposes the one thing external systems
 * actually need - "what state is this workflow in" - and nothing else.
 *
 * <p>Authorization uses {@code SCOPE_} authorities, which only {@link ApiKeyPrincipal} can carry, so
 * these checks can never be satisfied by a user's JWT and vice versa.
 *
 * <p>Tenant isolation is unchanged: ApiKeyAuthenticationFilter binds TenantContext from the key's
 * own tenant, and WorkflowService reads it, so a key can only ever see its owner's data.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicApiController {

    private final WorkflowService workflowService;

    /** Liveness probe for integrators; requires a valid key but no particular scope. */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('SCOPE_WORKFLOWS_READ')")
    public ResponseEntity<List<WorkflowDefinitionResponse>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listDefinitions());
    }

    @GetMapping("/instances")
    @PreAuthorize("hasAuthority('SCOPE_INSTANCES_READ')")
    public ResponseEntity<List<WorkflowInstanceResponse>> listInstances() {
        return ResponseEntity.ok(workflowService.listInstances());
    }

    /** The endpoint this whole surface exists for: external status lookup by id. */
    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAuthority('SCOPE_INSTANCES_READ')")
    public ResponseEntity<WorkflowInstanceResponse> getInstance(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getInstance(id));
    }
}
