package com.zentrox.forge.dashboard.dto;

import com.zentrox.forge.entity.Dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(UUID id, String name, boolean shared, boolean isDefault,
                                 String layoutJson, List<WidgetResponse> widgets,
                                 Instant createdAt, Instant updatedAt) {

    public static DashboardResponse from(Dashboard dashboard, List<WidgetResponse> widgets) {
        return new DashboardResponse(
                dashboard.getId(),
                dashboard.getName(),
                dashboard.isShared(),
                dashboard.isDefaultDashboard(),
                dashboard.getLayoutJson(),
                widgets,
                dashboard.getCreatedAt(),
                dashboard.getUpdatedAt());
    }
}
