package com.zentrox.forge.dashboard;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SCAFFOLD ONLY - FRD Section 11 (Configurable Dashboards).
 *
 * TODO(FRD-11.1): Define `dashboards` (tenant_id, owner_user_id, name, layout_json) and
 *   `dashboard_widgets` (dashboard_id, widget_type, config_json, position) tables.
 * TODO(FRD-11.2): Widget data sources should read through the existing tenant-scoped
 *   repositories (see repository.tenant) so dashboards inherit the same isolation
 *   guarantees as the rest of the platform - do not introduce a parallel query path.
 */
@Service
public class DashboardService {

    public Object getDashboardLayout(UUID tenantId, UUID dashboardId) {
        throw new UnsupportedOperationException("Configurable dashboards are not implemented yet - see FRD Section 11");
    }
}
