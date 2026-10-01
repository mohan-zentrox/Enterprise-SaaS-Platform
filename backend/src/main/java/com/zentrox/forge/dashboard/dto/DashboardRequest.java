package com.zentrox.forge.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code shared} true creates a dashboard visible to the whole tenant; false (default) keeps it
 * private to the caller.
 *
 * Widgets are sent as the complete desired set. A PUT replaces them wholesale rather than offering
 * per-widget endpoints - a layout is edited as a unit, and partial updates would need ordering
 * reconciliation for no benefit.
 */
public record DashboardRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        boolean shared,

        String layoutJson,

        @Valid
        List<WidgetRequest> widgets
) {
}
