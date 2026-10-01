package com.zentrox.forge.repository;

import com.zentrox.forge.entity.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Not tenant-scoped - see BillingWebhookEvent for why. */
public interface BillingWebhookEventRepository
        extends JpaRepository<BillingWebhookEvent, BillingWebhookEvent.Key> {
}
