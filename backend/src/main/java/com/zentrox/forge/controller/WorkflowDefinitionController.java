package com.zentrox.forge.controller;

import com.zentrox.forge.dto.workflow.WorkflowDefinitionRequest;
import com.zentrox.forge.dto.workflow.WorkflowDefinitionResponse;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.service.WorkflowService;
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
 * FRD Section 6: Workflow definitions (states/transitions). Every method is
 * {@code @PreAuthorize}-guarded against the Permission catalog; the tenant filter
 * itself is applied lower down, in WorkflowService / the tenant-scoped repositories.
 */
@RestController
@RequestMapping("/v1/workflows/definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowService workflowService;

    @PostMapping
    @PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_CREATE')")
    public ResponseEntity<WorkflowDefinitionResponse> create(@Valid @RequestBody WorkflowDefinitionRequest request) {
        UUID actor = SecurityUtils.currentUserId().orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.createDefinition(request, actor));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_READ')")
    public ResponseEntity<WorkflowDefinitionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getDefinition(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_READ')")
    public ResponseEntity<List<WorkflowDefinitionResponse>> list() {
        return ResponseEntity.ok(workflowService.listDefinitions());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_UPDATE')")
    public ResponseEntity<WorkflowDefinitionResponse> update(@PathVariable UUID id,
                                                               @Valid @RequestBody WorkflowDefinitionRequest request) {
        return ResponseEntity.ok(workflowService.updateDefinition(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.deleteDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
