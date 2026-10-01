package com.zentrox.forge.dashboard.dto;

import com.zentrox.forge.dashboard.WidgetType;
import com.zentrox.forge.entity.DashboardWidget;

import java.util.UUID;

/**
 * {@code data} is the resolved widget content, computed server-side on read. It is not persisted -
 * a dashboard stores what to show, never a stale copy of the numbers.
 */
public record WidgetResponse(UUID id, WidgetType widgetType, String title, String configJson,
                              int position, Object data) {

    public static WidgetResponse from(DashboardWidget widget, Object data) {
        return new WidgetResponse(widget.getId(), widget.getWidgetType(), widget.getTitle(),
                widget.getConfigJson(), widget.getPosition(), data);
    }
}
