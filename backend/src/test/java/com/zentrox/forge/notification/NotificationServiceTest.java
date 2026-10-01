package com.zentrox.forge.notification;

import com.zentrox.forge.entity.Notification;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.notification.dto.NotificationResponse;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.NotificationRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.CustomUserDetails;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An inbox is the most tempting place to leak data across users, not just across tenants: the
 * obvious implementation filters by tenant and forgets that a notification also belongs to exactly
 * one person. These tests pin both boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private User alice;
    private User bob;
    private User tenantBUser;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(newTenant("notif-a"));
        tenantB = tenantRepository.save(newTenant("notif-b"));
        alice = newUser(tenantA.getId(), "alice@notif-a.example");
        bob = newUser(tenantA.getId(), "bob@notif-a.example");
        tenantBUser = newUser(tenantB.getId(), "carol@notif-b.example");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void deliversAnInAppNotificationToItsRecipient() {
        UUID id = notificationService.notifyInApp(
                tenantA.getId(), alice.getId(), "USER_INVITED", "Welcome", "Body text", null);

        assertThat(id).isNotNull();
        Notification stored = notificationRepository.findByIdAndTenantId(id, tenantA.getId()).orElseThrow();
        assertThat(stored.getRecipientUserId()).isEqualTo(alice.getId());
        assertThat(stored.getReadAt()).isNull();
        assertThat(stored.isRead()).isFalse();
    }

    @Test
    void inboxShowsOnlyTheCallersOwnNotifications() {
        notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "For Alice", null, null);
        notificationService.notifyInApp(tenantA.getId(), bob.getId(), "T", "For Bob", null, null);

        authenticateAs(alice);
        var inbox = notificationService.listInbox(PageRequest.of(0, 20));

        assertThat(inbox.content()).extracting(NotificationResponse::title).containsExactly("For Alice");
    }

    @Test
    void inboxNeverCrossesTenants() {
        notificationService.notifyInApp(tenantB.getId(), tenantBUser.getId(), "T", "Tenant B secret", null, null);

        authenticateAs(alice);
        var inbox = notificationService.listInbox(PageRequest.of(0, 20));

        assertThat(inbox.content()).isEmpty();
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() {
        UUID bobsNotification = notificationService.notifyInApp(
                tenantA.getId(), bob.getId(), "T", "For Bob", null, null);

        authenticateAs(alice);

        // Same tenant, so a tenant-only filter would have allowed this.
        assertThatThrownBy(() -> notificationService.markRead(bobsNotification))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unreadCountCountsOnlyUnreadOwnNotifications() {
        notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "One", null, null);
        UUID second = notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "Two", null, null);
        notificationService.notifyInApp(tenantA.getId(), bob.getId(), "T", "Bob's", null, null);

        authenticateAs(alice);
        assertThat(notificationService.unreadCount()).isEqualTo(2);

        notificationService.markRead(second);
        assertThat(notificationService.unreadCount()).isEqualTo(1);
    }

    @Test
    void markReadIsIdempotentAndKeepsTheOriginalTimestamp() {
        UUID id = notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "One", null, null);
        authenticateAs(alice);

        NotificationResponse first = notificationService.markRead(id);
        NotificationResponse second = notificationService.markRead(id);

        assertThat(first.read()).isTrue();
        assertThat(second.readAt()).isEqualTo(first.readAt());
    }

    @Test
    void markAllReadAffectsOnlyTheCallerAndReportsHowMany() {
        notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "A1", null, null);
        notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "A2", null, null);
        UUID bobs = notificationService.notifyInApp(tenantA.getId(), bob.getId(), "T", "B1", null, null);

        authenticateAs(alice);
        assertThat(notificationService.markAllRead()).isEqualTo(2);
        assertThat(notificationService.unreadCount()).isZero();

        // Bob's notification must be untouched by Alice's bulk update.
        assertThat(notificationRepository.findByIdAndTenantId(bobs, tenantA.getId()).orElseThrow().getReadAt())
                .isNull();
    }

    @Test
    void markAllReadIsIdempotent() {
        notificationService.notifyInApp(tenantA.getId(), alice.getId(), "T", "A1", null, null);
        authenticateAs(alice);

        assertThat(notificationService.markAllRead()).isEqualTo(1);
        assertThat(notificationService.markAllRead()).isZero();
    }

    private Tenant newTenant(String slug) {
        return tenantRepository.save(
                Tenant.builder().name(slug).slug(slug).status(TenantStatus.ACTIVE).build());
    }

    private User newUser(UUID tenantId, String email) {
        Role role = roleRepository.findByTenantIdAndName(tenantId, "MEMBER").orElseGet(() ->
                roleRepository.save(Role.builder()
                        .tenantId(tenantId).name("MEMBER").systemRole(true)
                        .permissions(EnumSet.of(Permission.USER_READ)).build()));
        return userRepository.save(User.builder()
                .tenantId(tenantId)
                .email(email)
                .passwordHash("unused")
                .fullName(email)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void authenticateAs(User user) {
        TenantContext.setTenantId(user.getTenantId());
        CustomUserDetails principal = new CustomUserDetails(
                user.getId(), user.getTenantId(), user.getEmail(), "MEMBER", Set.of("USER_READ"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
