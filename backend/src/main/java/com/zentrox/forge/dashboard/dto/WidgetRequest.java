package com.zentrox.forge.dashboard.dto;

import com.zentrox.forge.dashboard.WidgetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WidgetRequest(

        @NotNull
        WidgetType widgetType,

        @Size(max = 255)
        String title,

        String configJson,

        @PositiveOrZero
        int position
) {
}
