package com.zentrox.forge.entity;

import com.zentrox.forge.billing.UsageMetric;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A metered counter, keyed by (tenant, metric).
 *
 * Note this does NOT extend TenantScopedEntity: it has a composite natural key rather than a
 * surrogate UUID id, so the tenant-scoped base class (which assumes a single UUID {@code id})
 * does not fit. The tenant column and the Hibernate filter are declared directly instead, and the
 * repository exposes only tenant-qualified lookups - the isolation guarantee is unchanged, only
 * the mechanism differs.
 */
@Entity
@Table(name = "tenant_usage")
@IdClass(TenantUsage.Key.class)
@FilterDef(name = "tenantUsageFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantUsageFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUsage {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private UsageMetric metric;

    @Column(nullable = false)
    @Builder.Default
    private long used = 0L;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Composite key. Must be Serializable with equals/hashCode for @IdClass. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID tenantId;
        private UsageMetric metric;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return java.util.Objects.equals(tenantId, other.tenantId) && metric == other.metric;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, metric);
        }
    }
}
