package com.zentrox.forge.dashboard;

import com.zentrox.forge.billing.BillingService;
import com.zentrox.forge.billing.EntitlementService;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.entity.WorkflowInstance;
import com.zentrox.forge.repository.tenant.AuditLogRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.repository.tenant.WorkflowDefinitionRepository;
import com.zentrox.forge.repository.tenant.WorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves each {@link WidgetType} to its data (FRD-11.2).
 *
 * <p><b>Everything here reads through the existing tenant-scoped repositories</b>, with the tenant
 * passed in explicitly. This is the single most important property of the module: the tempting
 * implementation of a dashboard is a set of hand-written aggregate SQL queries, and every one of
 * those is a fresh opportunity to omit the tenant predicate. Reusing the repositories means
 * dashboards inherit the same isolation guarantees as the rest of the platform - there is no
 * parallel query path to audit.
 *
 * <p>Widget data is computed on read and never cached. A dashboard that silently shows yesterday's
 * numbers is worse than a slightly slower one; caching belongs behind a deliberate decision about
 * staleness, not as an implementation detail here.
 */
@Component
@RequiredArgsConstructor
public class WidgetDataProvider {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final EntitlementService entitlementService;
    private final BillingService billingService;

    public Object dataFor(WidgetType type, UUID tenantId) {
        return switch (type) {
            case WORKFLOW_DEFINITION_COUNT ->
                    Map.of("count", definitionRepository.countByTenantId(tenantId));

            case WORKFLOW_INSTANCE_COUNT ->
                    Map.of("count", instanceRepository.countByTenantId(tenantId));

            case ACTIVE_USER_COUNT ->
                    Map.of("count", userRepository.countByTenantIdAndStatus(tenantId, UserStatus.ACTIVE));

            case INSTANCES_BY_STATE -> instancesByState(tenantId);

            case RECENT_AUDIT_ACTIVITY -> recentActivity(tenantId);

            case SUBSCRIPTION_USAGE -> Map.of(
                    "plan", entitlementService.currentPlan(tenantId).name(),
                    "status", entitlementService.currentSubscription(tenantId).getStatus().name(),
                    "usage", billingService.usageFor(tenantId, entitlementService.currentPlan(tenantId)));
        };
    }

    /**
     * Grouped in memory rather than with a {@code GROUP BY}. Honest trade-off: it costs one full read
     * of the tenant's instances, which is fine at the scale the plan limits permit (25k on
     * PROFESSIONAL) and avoids a bespoke aggregate query that would have to re-state the tenant
     * predicate. If this becomes hot, the fix is a projection on the repository - still tenant-scoped -
     * not a raw query here.
     */
    private Map<String, Long> instancesByState(UUID tenantId) {
        return instanceRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(
                        WorkflowInstance::getCurrentState,
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private List<Map<String, Object>> recentActivity(UUID tenantId) {
        return auditLogRepository
                .findAllByTenantIdOrderByTimestampDesc(tenantId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .getContent().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("action", entry.getAction());
                    row.put("entityType", entry.getEntityType());
                    row.put("occurredAt", entry.getTimestamp());
                    row.put("actorUserId", entry.getActorUserId());
                    return row;
                })
                .toList();
    }
}
