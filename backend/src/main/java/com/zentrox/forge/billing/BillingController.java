package com.zentrox.forge.billing;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCAFFOLD ONLY - FRD Section 9 (Subscription & Billing).
 *
 * TODO(FRD-9.4): Add endpoints for: current subscription, plan change/upgrade,
 *   invoice history, payment method management, and the payment provider webhook
 *   receiver (signature-verified, idempotent).
 */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    @GetMapping("/subscription")
    public ResponseEntity<Void> currentSubscription() {
        // TODO(FRD-9.1): return the tenant's current SubscriptionPlan + status once persisted.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
