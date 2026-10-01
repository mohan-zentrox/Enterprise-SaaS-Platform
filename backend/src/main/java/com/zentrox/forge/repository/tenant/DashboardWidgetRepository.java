package com.zentrox.forge.repository.tenant;

import com.zentrox.forge.entity.DashboardWidget;

import java.util.List;
import java.util.UUID;

public interface DashboardWidgetRepository extends TenantScopedRepository<DashboardWidget, UUID> {

    List<DashboardWidget> findAllByTenantIdAndDashboardIdOrderByPositionAsc(UUID tenantId, UUID dashboardId);

    void deleteAllByTenantIdAndDashboardId(UUID tenantId, UUID dashboardId);
}
