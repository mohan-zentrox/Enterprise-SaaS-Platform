package com.zentrox.forge.dashboard;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.dashboard.dto.DashboardRequest;
import com.zentrox.forge.dashboard.dto.DashboardResponse;
import com.zentrox.forge.dashboard.dto.WidgetRequest;
import com.zentrox.forge.dashboard.dto.WidgetResponse;
import com.zentrox.forge.entity.Dashboard;
import com.zentrox.forge.entity.DashboardWidget;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.tenant.DashboardRepository;
import com.zentrox.forge.repository.tenant.DashboardWidgetRepository;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * FRD Section 11 - configurable dashboards.
 *
 * <p>Visibility rules are enforced in the repository queries, not here: a caller sees their own
 * dashboards plus the tenant's shared ones, and a private dashboard cannot be opened by id by anyone
 * but its owner. Writes are stricter still - only the owner may modify a private dashboard, and
 * changing a shared one requires TENANT_UPDATE (checked at the controller), because a shared
 * dashboard is effectively tenant configuration.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MAX_WIDGETS_PER_DASHBOARD = 24;

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository widgetRepository;
    private final WidgetDataProvider widgetDataProvider;

    public List<DashboardResponse> listDashboards() {
        UUID tenantId = TenantContext.requireTenantId();
        return dashboardRepository.findVisibleTo(tenantId, currentUserId()).stream()
                // Summary form: widget data is resolved only when a single dashboard is opened.
                // Rendering every widget of every dashboard for a list view would multiply the work
                // by the number of dashboards for data nobody is looking at yet.
                .map(d -> DashboardResponse.from(d, List.of()))
                .toList();
    }

    public DashboardResponse getDashboard(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        Dashboard dashboard = requireVisible(id, tenantId);
        return DashboardResponse.from(dashboard, resolveWidgets(tenantId, dashboard.getId()));
    }

    @Audited(action = "DASHBOARD_CREATE", entityType = "Dashboard")
    @Transactional
    public DashboardResponse createDashboard(DashboardRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateWidgets(request.widgets());

        if (dashboardRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new ConflictException("A dashboard named '" + request.name() + "' already exists");
        }

        Dashboard dashboard = dashboardRepository.save(Dashboard.builder()
                .tenantId(tenantId)
                .ownerUserId(request.shared() ? null : currentUserId())
                .name(request.name())
                .layoutJson(request.layoutJson() == null ? "{}" : request.layoutJson())
                .build());

        replaceWidgets(tenantId, dashboard.getId(), request.widgets());
        return DashboardResponse.from(dashboard, resolveWidgets(tenantId, dashboard.getId()));
    }

    @Audited(action = "DASHBOARD_UPDATE", entityType = "Dashboard")
    @Transactional
    public DashboardResponse updateDashboard(UUID id, DashboardRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateWidgets(request.widgets());

        Dashboard dashboard = requireWritable(id, tenantId);

        if (!dashboard.getName().equals(request.name())
                && dashboardRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new ConflictException("A dashboard named '" + request.name() + "' already exists");
        }

        dashboard.setName(request.name());
        if (request.layoutJson() != null) {
            dashboard.setLayoutJson(request.layoutJson());
        }
        dashboard.setOwnerUserId(request.shared() ? null : currentUserId());
        dashboard = dashboardRepository.save(dashboard);

        replaceWidgets(tenantId, dashboard.getId(), request.widgets());
        return DashboardResponse.from(dashboard, resolveWidgets(tenantId, dashboard.getId()));
    }

    @Audited(action = "DASHBOARD_DELETE", entityType = "Dashboard")
    @Transactional
    public void deleteDashboard(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        requireWritable(id, tenantId);

        // Widgets are removed explicitly rather than relying on the schema's ON DELETE CASCADE:
        // Hibernate's first-level cache would otherwise still hold widget entities that no longer
        // exist, and the cascade is invisible to any @Version/audit logic added later.
        widgetRepository.deleteAllByTenantIdAndDashboardId(tenantId, id);
        dashboardRepository.deleteByIdAndTenantId(id, tenantId);
    }

    // ------------------------------------------------------------------ helpers

    private List<WidgetResponse> resolveWidgets(UUID tenantId, UUID dashboardId) {
        return widgetRepository.findAllByTenantIdAndDashboardIdOrderByPositionAsc(tenantId, dashboardId).stream()
                .map(widget -> WidgetResponse.from(
                        widget, widgetDataProvider.dataFor(widget.getWidgetType(), tenantId)))
                .toList();
    }

    private void replaceWidgets(UUID tenantId, UUID dashboardId, List<WidgetRequest> widgets) {
        widgetRepository.deleteAllByTenantIdAndDashboardId(tenantId, dashboardId);
        if (widgets == null) {
            return;
        }
        for (WidgetRequest widget : widgets) {
            widgetRepository.save(DashboardWidget.builder()
                    .tenantId(tenantId)
                    .dashboardId(dashboardId)
                    .widgetType(widget.widgetType())
                    .title(widget.title())
                    .configJson(widget.configJson() == null ? "{}" : widget.configJson())
                    .position(widget.position())
                    .build());
        }
    }

    /**
     * Bounded because every widget on an opened dashboard costs at least one query - an unbounded
     * layout is a self-inflicted denial of service on the tenant's own database.
     */
    private void validateWidgets(List<WidgetRequest> widgets) {
        if (widgets != null && widgets.size() > MAX_WIDGETS_PER_DASHBOARD) {
            throw new ConflictException(
                    "A dashboard may hold at most " + MAX_WIDGETS_PER_DASHBOARD + " widgets");
        }
    }

    private Dashboard requireVisible(UUID id, UUID tenantId) {
        return dashboardRepository.findVisibleToById(id, tenantId, currentUserId())
                .orElseThrow(() -> new NotFoundException("Dashboard not found: " + id));
    }

    /**
     * Read access is not write access. A shared dashboard is visible to everyone in the tenant but
     * must not be editable by everyone - the controller requires TENANT_UPDATE for that, and this
     * check stops a user editing someone else's private dashboard.
     */
    private Dashboard requireWritable(UUID id, UUID tenantId) {
        Dashboard dashboard = requireVisible(id, tenantId);
        UUID callerId = currentUserId();
        if (!dashboard.isShared() && !dashboard.getOwnerUserId().equals(callerId)) {
            throw new NotFoundException("Dashboard not found: " + id);
        }
        return dashboard;
    }

    private UUID currentUserId() {
        return SecurityUtils.currentUserId().orElseThrow(() -> new IllegalStateException(
                "No authenticated user bound to the request; dashboards are per-user"));
    }
}
