package com.zentrox.forge.billing;

import com.zentrox.forge.billing.dto.PlanChangeRequest;
import com.zentrox.forge.billing.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FRD Section 9 - the tenant's own subscription.
 *
 * Reads require TENANT_READ (every system role has it, so any member can see the plan they are
 * working under). Changing the plan is a commercial commitment, so it requires TENANT_UPDATE.
 */
@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/subscription")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public ResponseEntity<SubscriptionResponse> currentSubscription() {
        return ResponseEntity.ok(billingService.currentSubscription());
    }

    @PostMapping("/subscription/plan")
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<SubscriptionResponse> changePlan(@Valid @RequestBody PlanChangeRequest request) {
        return ResponseEntity.ok(billingService.changePlan(request.plan()));
    }
}
