package com.zentrox.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Idempotency ledger for payment-provider webhooks.
 *
 * Not tenant-scoped: a webhook arrives before we know which tenant it concerns, and the whole point
 * of this row is to be insertable at that moment. The composite primary key (provider, event_id) is
 * the deduplication mechanism - see BillingWebhookService#handle.
 *
 * <p><b>Implements {@link Persistable} with {@code isNew() == true} deliberately.</b> With an
 * assigned (non-generated) id, Spring Data's {@code save()} considers the entity already-existing and
 * issues {@code merge()} - which for a replayed event is a silent UPDATE of the ledger row, so the
 * duplicate is never detected and the event is applied twice. Forcing {@code persist()} makes the
 * insert actually hit the primary key, which is what turns the constraint into the idempotency
 * guarantee. Safe because these rows are append-only: nothing ever updates one.
 */
@Entity
@Table(name = "billing_webhook_events")
@IdClass(BillingWebhookEvent.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingWebhookEvent implements Persistable<BillingWebhookEvent.Key> {

    @Id
    @Column(nullable = false, length = 50)
    private String provider;

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Override
    public Key getId() {
        return new Key(provider, eventId);
    }

    /** Always an insert - see the class javadoc. */
    @Override
    public boolean isNew() {
        return true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String provider;
        private String eventId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return Objects.equals(provider, other.provider) && Objects.equals(eventId, other.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, eventId);
        }
    }
}
