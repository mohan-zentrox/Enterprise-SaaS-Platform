package com.zentrox.forge.controller;

import com.zentrox.forge.dto.workflow.WorkflowInstanceCreateRequest;
import com.zentrox.forge.dto.workflow.WorkflowInstanceResponse;
import com.zentrox.forge.dto.workflow.WorkflowInstanceTransitionRequest;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** FRD Section 6: Workflow instances (current state + append-only transition history). */
@RestController
@RequestMapping("/v1/workflows/instances")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowService workflowService;

    @PostMapping
    @PreAuthorize("hasAuthority('WORKFLOW_INSTANCE_CREATE')")
    public ResponseEntity<WorkflowInstanceResponse> create(@Valid @RequestBody WorkflowInstanceCreateRequest request) {
        UUID actor = SecurityUtils.currentUserId().orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.createInstance(request, actor));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WORKFLOW_INSTANCE_READ')")
    public ResponseEntity<WorkflowInstanceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getInstance(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WORKFLOW_INSTANCE_READ')")
    public ResponseEntity<List<WorkflowInstanceResponse>> list() {
        return ResponseEntity.ok(workflowService.listInstances());
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('WORKFLOW_INSTANCE_TRANSITION')")
    public ResponseEntity<WorkflowInstanceResponse> transition(@PathVariable UUID id,
                                                                 @Valid @RequestBody WorkflowInstanceTransitionRequest request) {
        UUID actor = SecurityUtils.currentUserId().orElseThrow();
        return ResponseEntity.ok(workflowService.transitionInstance(id, request, actor));
    }
}
