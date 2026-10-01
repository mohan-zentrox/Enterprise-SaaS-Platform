package com.zentrox.forge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.billing.RequiresEntitlement;
import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.dto.workflow.WorkflowDefinitionPayload;
import com.zentrox.forge.dto.workflow.WorkflowDefinitionRequest;
import com.zentrox.forge.dto.workflow.WorkflowDefinitionResponse;
import com.zentrox.forge.dto.workflow.WorkflowHistoryEntry;
import com.zentrox.forge.dto.workflow.WorkflowInstanceCreateRequest;
import com.zentrox.forge.dto.workflow.WorkflowInstanceResponse;
import com.zentrox.forge.dto.workflow.WorkflowInstanceTransitionRequest;
import com.zentrox.forge.dto.workflow.WorkflowTransitionRule;
import com.zentrox.forge.entity.WorkflowDefinition;
import com.zentrox.forge.entity.WorkflowInstance;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.InvalidTransitionException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.notification.event.WorkflowTransitionedEvent;
import com.zentrox.forge.repository.tenant.WorkflowDefinitionRepository;
import com.zentrox.forge.repository.tenant.WorkflowInstanceRepository;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FRD Section 6 (Workflows) - the concrete tenant-isolated resource module. Every
 * lookup goes through the tenant-scoped repositories (findByIdAndTenantId /
 * findAllByTenantId), never the disabled tenant-unaware accessors - see
 * repository.tenant.TenantScopedRepositoryImpl for why that's structurally enforced,
 * not just a convention.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------- definitions

    @Audited(action = "WORKFLOW_DEFINITION_CREATE", entityType = "WorkflowDefinition")
    @RequiresEntitlement(UsageMetric.WORKFLOW_DEFINITIONS)
    @Transactional
    public WorkflowDefinitionResponse createDefinition(WorkflowDefinitionRequest request, UUID actorUserId) {
        UUID tenantId = TenantContext.requireTenantId();
        validatePayload(request.states(), request.transitions());

        if (definitionRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new ConflictException("A workflow named '" + request.name() + "' already exists");
        }

        WorkflowDefinitionPayload payload = new WorkflowDefinitionPayload(request.states(), normalizeTransitions(request.transitions()));
        WorkflowDefinition entity = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .name(request.name())
                .description(request.description())
                .definitionJson(writeJson(payload))
                .version(1)
                .createdBy(actorUserId)
                .build();

        entity = definitionRepository.save(entity);
        return WorkflowDefinitionResponse.from(entity, payload);
    }

    public WorkflowDefinitionResponse getDefinition(UUID id) {
        WorkflowDefinition entity = requireDefinition(id);
        return WorkflowDefinitionResponse.from(entity, readPayload(entity));
    }

    public List<WorkflowDefinitionResponse> listDefinitions() {
        UUID tenantId = TenantContext.requireTenantId();
        return definitionRepository.findAllByTenantId(tenantId).stream()
                .map(e -> WorkflowDefinitionResponse.from(e, readPayload(e)))
                .toList();
    }

    @Audited(action = "WORKFLOW_DEFINITION_UPDATE", entityType = "WorkflowDefinition")
    @Transactional
    public WorkflowDefinitionResponse updateDefinition(UUID id, WorkflowDefinitionRequest request) {
        validatePayload(request.states(), request.transitions());
        WorkflowDefinition entity = requireDefinition(id);

        WorkflowDefinitionPayload payload = new WorkflowDefinitionPayload(request.states(), normalizeTransitions(request.transitions()));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDefinitionJson(writeJson(payload));
        entity.setVersion(entity.getVersion() + 1);

        entity = definitionRepository.save(entity);
        return WorkflowDefinitionResponse.from(entity, payload);
    }

    @Audited(action = "WORKFLOW_DEFINITION_DELETE", entityType = "WorkflowDefinition")
    @Transactional
    public void deleteDefinition(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        requireDefinition(id); // 404s before attempting delete if it doesn't belong to this tenant
        definitionRepository.deleteByIdAndTenantId(id, tenantId);
    }

    // ------------------------------------------------------------------ instances

    @Audited(action = "WORKFLOW_INSTANCE_CREATE", entityType = "WorkflowInstance")
    // Metered, not counted: "instances this month" is a rate, so there is no population to count.
    @RequiresEntitlement(value = UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, meter = true)
    @Transactional
    public WorkflowInstanceResponse createInstance(WorkflowInstanceCreateRequest request, UUID actorUserId) {
        UUID tenantId = TenantContext.requireTenantId();
        WorkflowDefinition definition = requireDefinition(request.workflowDefinitionId());
        WorkflowDefinitionPayload payload = readPayload(definition);

        if (payload.states().isEmpty()) {
            throw new InvalidTransitionException("Workflow definition has no states to start from");
        }

        WorkflowInstance instance = WorkflowInstance.builder()
                .tenantId(tenantId)
                .workflowDefinitionId(definition.getId())
                .currentState(payload.states().get(0))
                .historyJson(writeJson(List.<WorkflowHistoryEntry>of()))
                .createdBy(actorUserId)
                .build();

        instance = instanceRepository.save(instance);
        return WorkflowInstanceResponse.from(instance, List.of());
    }

    public WorkflowInstanceResponse getInstance(UUID id) {
        WorkflowInstance instance = requireInstance(id);
        return WorkflowInstanceResponse.from(instance, readHistory(instance));
    }

    public List<WorkflowInstanceResponse> listInstances() {
        UUID tenantId = TenantContext.requireTenantId();
        return instanceRepository.findAllByTenantId(tenantId).stream()
                .map(i -> WorkflowInstanceResponse.from(i, readHistory(i)))
                .toList();
    }

    @Audited(action = "WORKFLOW_INSTANCE_TRANSITION", entityType = "WorkflowInstance")
    @Transactional
    public WorkflowInstanceResponse transitionInstance(UUID id, WorkflowInstanceTransitionRequest request,
                                                         UUID actorUserId) {
        WorkflowInstance instance = requireInstance(id);
        WorkflowDefinition definition = requireDefinition(instance.getWorkflowDefinitionId());
        WorkflowDefinitionPayload payload = readPayload(definition);

        // Captured in the lambda below, so it must not be the (later reassigned) `instance` reference.
        final String fromState = instance.getCurrentState();
        boolean allowed = payload.transitions().stream()
                .anyMatch(t -> t.from().equals(fromState) && t.to().equals(request.toState()));
        if (!allowed) {
            throw new InvalidTransitionException(
                    "Transition from '" + fromState + "' to '" + request.toState()
                            + "' is not defined on workflow '" + definition.getName() + "'");
        }

        List<WorkflowHistoryEntry> history = new ArrayList<>(readHistory(instance));
        history.add(new WorkflowHistoryEntry(fromState, request.toState(), Instant.now(), actorUserId));

        instance.setCurrentState(request.toState());
        instance.setHistoryJson(writeJson(history));
        instance = instanceRepository.save(instance);

        eventPublisher.publishEvent(new WorkflowTransitionedEvent(
                instance.getTenantId(), instance.getId(), definition.getName(), fromState,
                request.toState(), actorUserId));

        return WorkflowInstanceResponse.from(instance, history);
    }

    // -------------------------------------------------------------------- helpers

    private WorkflowDefinition requireDefinition(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return definitionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Workflow definition not found: " + id));
    }

    private WorkflowInstance requireInstance(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return instanceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Workflow instance not found: " + id));
    }

    private void validatePayload(List<String> states, List<WorkflowTransitionRule> transitions) {
        if (transitions == null) {
            return;
        }
        for (WorkflowTransitionRule rule : transitions) {
            if (!states.contains(rule.from()) || !states.contains(rule.to())) {
                throw new InvalidTransitionException(
                        "Transition " + rule + " references a state not declared in 'states'");
            }
        }
    }

    /** A client may omit "transitions" entirely; treat that as "no transitions defined" rather than null. */
    private List<WorkflowTransitionRule> normalizeTransitions(List<WorkflowTransitionRule> transitions) {
        return transitions == null ? List.of() : transitions;
    }

    private WorkflowDefinitionPayload readPayload(WorkflowDefinition entity) {
        try {
            WorkflowDefinitionPayload payload =
                    objectMapper.readValue(entity.getDefinitionJson(), WorkflowDefinitionPayload.class);
            return new WorkflowDefinitionPayload(payload.states(), normalizeTransitions(payload.transitions()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt definition_json for workflow " + entity.getId(), e);
        }
    }

    private List<WorkflowHistoryEntry> readHistory(WorkflowInstance instance) {
        try {
            return objectMapper.readValue(instance.getHistoryJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, WorkflowHistoryEntry.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt history_json for instance " + instance.getId(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize workflow JSON payload", e);
        }
    }
}
