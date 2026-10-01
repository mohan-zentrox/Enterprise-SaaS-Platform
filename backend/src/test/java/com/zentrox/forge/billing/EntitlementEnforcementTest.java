package com.zentrox.forge.billing;

import com.zentrox.forge.dto.user.UserCreateRequest;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Subscription;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.EntitlementExceededException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.SubscriptionRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.service.RoleCatalog;
import com.zentrox.forge.service.UserManagementService;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the {@code @RequiresEntitlement} aspect actually gates writes - i.e. that enforcement is
 * live rather than merely annotated. The seat limit is used because it is counted live, which is the
 * case most likely to be wrong.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntitlementEnforcementTest {

    @Autowired
    private EntitlementService entitlementService;
    @Autowired
    private UserManagementService userManagementService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Tenant tenant;
    private Role memberRole;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("ent-tenant").slug("ent-tenant").status(TenantStatus.ACTIVE).build());
        memberRole = roleRepository.save(Role.builder()
                .tenantId(tenant.getId()).name(RoleCatalog.MEMBER).systemRole(true)
                .permissions(EnumSet.of(Permission.USER_READ)).build());
        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Subscription subscribe(SubscriptionPlan plan, SubscriptionStatus status) {
        return subscriptionRepository.save(Subscription.builder()
                .tenantId(tenant.getId()).plan(plan).status(status).build());
    }

    @Test
    void freePlanReportsItsSeatLimit() {
        subscribe(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE);
        assertThat(entitlementService.limit(tenant.getId(), UsageMetric.SEATS)).isEqualTo(3);
    }

    @Test
    void enterprisePlanIsUnlimited() {
        subscribe(SubscriptionPlan.ENTERPRISE, SubscriptionStatus.ACTIVE);
        assertThat(entitlementService.limit(tenant.getId(), UsageMetric.SEATS))
                .isEqualTo(SubscriptionPlan.UNLIMITED);
        assertThat(entitlementService.isEntitled(tenant.getId(), UsageMetric.SEATS)).isTrue();
    }

    @Test
    void invitingBeyondTheSeatLimitIsRejectedWith402() {
        subscribe(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE); // 3 seats

        for (int i = 0; i < 3; i++) {
            userManagementService.createUser(new UserCreateRequest(
                    "Person " + i, "p" + i + "@ent-tenant.example", "a-strong-password", RoleCatalog.MEMBER));
        }

        assertThatThrownBy(() -> userManagementService.createUser(new UserCreateRequest(
                "One Too Many", "extra@ent-tenant.example", "a-strong-password", RoleCatalog.MEMBER)))
                .isInstanceOf(EntitlementExceededException.class)
                .hasMessageContaining("FREE")
                .hasMessageContaining("Upgrade");
    }

    @Test
    void deactivatedUsersDoNotConsumeASeat() {
        subscribe(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE);
        seedUser("gone@ent-tenant.example", UserStatus.DISABLED);
        seedUser("here@ent-tenant.example", UserStatus.ACTIVE);

        // Two rows exist, only one is active - a seat count that ignored status would say 2.
        assertThat(entitlementService.currentUsage(tenant.getId(), UsageMetric.SEATS)).isEqualTo(1);
    }

    @Test
    void aPastDueSubscriptionBlocksWritesRegardlessOfLimits() {
        subscribe(SubscriptionPlan.ENTERPRISE, SubscriptionStatus.PAST_DUE);

        assertThatThrownBy(() -> entitlementService.requireEntitlement(tenant.getId(), UsageMetric.SEATS))
                .isInstanceOf(EntitlementExceededException.class)
                .hasMessageContaining("past due")
                // The message must reassure them their data is intact, not just refuse.
                .hasMessageContaining("readable");
    }

    @Test
    void aTrialingSubscriptionPermitsWrites() {
        subscribe(SubscriptionPlan.PROFESSIONAL, SubscriptionStatus.TRIALING);
        assertThat(entitlementService.isEntitled(tenant.getId(), UsageMetric.SEATS)).isTrue();
    }

    @Test
    void aCanceledSubscriptionBlocksWrites() {
        subscribe(SubscriptionPlan.FREE, SubscriptionStatus.CANCELED);
        assertThat(entitlementService.isEntitled(tenant.getId(), UsageMetric.SEATS)).isFalse();
    }

    @Test
    void aTenantWithNoSubscriptionRowDefaultsToFreeRatherThanLockedOut() {
        // No subscribe() call at all. Failing closed here would lock a tenant out of their own data
        // because of a missing row, which is worse than briefly granting FREE limits.
        assertThat(entitlementService.currentPlan(tenant.getId())).isEqualTo(SubscriptionPlan.FREE);
        assertThat(entitlementService.isEntitled(tenant.getId(), UsageMetric.SEATS)).isTrue();
    }

    @Test
    void meteredUsageAccumulates() {
        subscribe(SubscriptionPlan.FREE, SubscriptionStatus.ACTIVE);

        entitlementService.recordUsage(tenant.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 1);
        entitlementService.recordUsage(tenant.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 1);
        entitlementService.recordUsage(tenant.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 3);

        assertThat(entitlementService.currentUsage(tenant.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH))
                .isEqualTo(5);
    }

    @Test
    void usageIsPerTenant() {
        Tenant other = tenantRepository.save(Tenant.builder()
                .name("other").slug("other-ent").status(TenantStatus.ACTIVE).build());

        entitlementService.recordUsage(tenant.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH, 7);

        assertThat(entitlementService.currentUsage(other.getId(), UsageMetric.WORKFLOW_INSTANCES_PER_MONTH))
                .isZero();
    }

    private User seedUser(String email, UserStatus status) {
        return userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .email(email)
                .passwordHash("unused")
                .fullName(email)
                .role(memberRole)
                .status(status)
                .build());
    }
}
