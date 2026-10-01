package com.zentrox.forge.entity;

import com.zentrox.forge.dashboard.WidgetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/** FRD Section 11 - one widget placed on a {@link Dashboard}. */
@Entity
@Table(name = "dashboard_widgets")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DashboardWidget extends TenantScopedEntity {

    @Column(name = "dashboard_id", nullable = false)
    private UUID dashboardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_type", nullable = false, length = 100)
    private WidgetType widgetType;

    private String title;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String configJson = "{}";

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
