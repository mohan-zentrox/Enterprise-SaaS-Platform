package com.zentrox.forge.dashboard;

import com.zentrox.forge.dashboard.dto.DashboardRequest;
import com.zentrox.forge.dashboard.dto.DashboardResponse;
import com.zentrox.forge.dashboard.dto.WidgetRequest;
import com.zentrox.forge.dashboard.dto.WidgetResponse;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.security.CustomUserDetails;
import com.zentrox.forge.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dashboards add a second visibility axis on top of tenancy - private vs shared - so these tests
 * check both: no cross-tenant leakage, and no reading another user's private dashboard inside the
 * same tenant.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;
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
    private User carol;

    @BeforeEach
    void setUp() {
        tenantA = newTenant("dash-a");
        tenantB = newTenant("dash-b");
        alice = newUser(tenantA.getId(), "alice@dash-a.example");
        bob = newUser(tenantA.getId(), "bob@dash-a.example");
        carol = newUser(tenantB.getId(), "carol@dash-b.example");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAPrivateDashboardWithWidgets() {
        authenticateAs(alice);

        DashboardResponse created = dashboardService.createDashboard(new DashboardRequest(
                "My board", false, "{\"cols\":2}",
                List.of(new WidgetRequest(WidgetType.WORKFLOW_DEFINITION_COUNT, "Definitions", null, 0),
                        new WidgetRequest(WidgetType.ACTIVE_USER_COUNT, "People", null, 1))));

        assertThat(created.shared()).isFalse();
        assertThat(created.widgets()).extracting(WidgetResponse::widgetType)
                .containsExactly(WidgetType.WORKFLOW_DEFINITION_COUNT, WidgetType.ACTIVE_USER_COUNT);
    }

    @Test
    void widgetDataIsResolvedFromTheCallersOwnTenant() {
        authenticateAs(alice);
        DashboardResponse created = dashboardService.createDashboard(new DashboardRequest(
                "Counts", false, null,
                List.of(new WidgetRequest(WidgetType.ACTIVE_USER_COUNT, "People", null, 0))));

        DashboardResponse opened = dashboardService.getDashboard(created.id());

        // Tenant A has alice and bob; carol is in tenant B and must not be counted.
        assertThat(opened.widgets()).hasSize(1);
        assertThat(opened.widgets().get(0).data().toString()).contains("2");
    }

    @Test
    void anotherUsersPrivateDashboardIsNotVisible() {
        authenticateAs(alice);
        DashboardResponse alices = dashboardService.createDashboard(
                new DashboardRequest("Alice private", false, null, List.of()));

        authenticateAs(bob);

        // Same tenant, so a tenant-only filter would have let Bob read it.
        assertThat(dashboardService.listDashboards()).extracting(DashboardResponse::id)
                .doesNotContain(alices.id());
        assertThatThrownBy(() -> dashboardService.getDashboard(alices.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSharedDashboardIsVisibleToEveryoneInTheTenant() {
        authenticateAs(alice);
        DashboardResponse shared = dashboardService.createDashboard(
                new DashboardRequest("Team board", true, null, List.of()));

        authenticateAs(bob);
        assertThat(dashboardService.listDashboards()).extracting(DashboardResponse::id).contains(shared.id());
        assertThat(dashboardService.getDashboard(shared.id()).shared()).isTrue();
    }

    @Test
    void aSharedDashboardIsNotVisibleToAnotherTenant() {
        authenticateAs(alice);
        DashboardResponse shared = dashboardService.createDashboard(
                new DashboardRequest("Team board", true, null, List.of()));

        authenticateAs(carol);

        // "Shared" means shared within one tenant, never across them.
        assertThat(dashboardService.listDashboards()).isEmpty();
        assertThatThrownBy(() -> dashboardService.getDashboard(shared.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotEditAnotherUsersPrivateDashboard() {
        authenticateAs(alice);
        DashboardResponse alices = dashboardService.createDashboard(
                new DashboardRequest("Alice private", false, null, List.of()));

        authenticateAs(bob);
        assertThatThrownBy(() -> dashboardService.updateDashboard(
                alices.id(), new DashboardRequest("Hijacked", false, null, List.of())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateReplacesTheWidgetSetWholesale() {
        authenticateAs(alice);
        DashboardResponse created = dashboardService.createDashboard(new DashboardRequest(
                "Board", false, null,
                List.of(new WidgetRequest(WidgetType.ACTIVE_USER_COUNT, "People", null, 0),
                        new WidgetRequest(WidgetType.WORKFLOW_INSTANCE_COUNT, "Instances", null, 1))));

        DashboardResponse updated = dashboardService.updateDashboard(created.id(), new DashboardRequest(
                "Board", false, null,
                List.of(new WidgetRequest(WidgetType.INSTANCES_BY_STATE, "By state", null, 0))));

        assertThat(updated.widgets()).extracting(WidgetResponse::widgetType)
                .containsExactly(WidgetType.INSTANCES_BY_STATE);
    }

    @Test
    void rejectsDuplicateNamesWithinATenant() {
        authenticateAs(alice);
        dashboardService.createDashboard(new DashboardRequest("Same", false, null, List.of()));

        assertThatThrownBy(() -> dashboardService.createDashboard(
                new DashboardRequest("Same", false, null, List.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void allowsTheSameDashboardNameInDifferentTenants() {
        authenticateAs(alice);
        dashboardService.createDashboard(new DashboardRequest("Overview", false, null, List.of()));

        authenticateAs(carol);
        assertThat(dashboardService.createDashboard(
                new DashboardRequest("Overview", false, null, List.of())).id()).isNotNull();
    }

    @Test
    void rejectsAnUnboundedWidgetCount() {
        authenticateAs(alice);
        List<WidgetRequest> tooMany = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> new WidgetRequest(WidgetType.ACTIVE_USER_COUNT, "W" + i, null, i))
                .toList();

        assertThatThrownBy(() -> dashboardService.createDashboard(
                new DashboardRequest("Huge", false, null, tooMany)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void deleteRemovesTheDashboardAndItsWidgets() {
        authenticateAs(alice);
        DashboardResponse created = dashboardService.createDashboard(new DashboardRequest(
                "Doomed", false, null,
                List.of(new WidgetRequest(WidgetType.ACTIVE_USER_COUNT, "People", null, 0))));

        dashboardService.deleteDashboard(created.id());

        assertThat(dashboardService.listDashboards()).extracting(DashboardResponse::id)
                .doesNotContain(created.id());
    }

    @Test
    void everyWidgetTypeResolvesWithoutError() {
        authenticateAs(alice);
        // A widget type added to the enum but not handled by WidgetDataProvider would throw here
        // rather than at a user's first page load.
        List<WidgetRequest> all = java.util.Arrays.stream(WidgetType.values())
                .map(t -> new WidgetRequest(t, t.name(), null, t.ordinal()))
                .toList();

        DashboardResponse created = dashboardService.createDashboard(
                new DashboardRequest("Everything", false, null, all));

        assertThat(created.widgets()).hasSize(WidgetType.values().length);
        assertThat(created.widgets()).allSatisfy(w -> assertThat(w.data()).isNotNull());
    }

    private Tenant newTenant(String slug) {
        return tenantRepository.save(
                Tenant.builder().name(slug).slug(slug).status(TenantStatus.ACTIVE).build());
    }

    private User newUser(UUID tenantId, String email) {
        Role role = roleRepository.findByTenantIdAndName(tenantId, "MEMBER").orElseGet(() ->
                roleRepository.save(Role.builder()
                        .tenantId(tenantId).name("MEMBER").systemRole(true)
                        .permissions(EnumSet.of(Permission.USER_READ, Permission.TENANT_READ)).build()));
        return userRepository.save(User.builder()
                .tenantId(tenantId).email(email).passwordHash("unused").fullName(email)
                .role(role).status(UserStatus.ACTIVE).build());
    }

    private void authenticateAs(User user) {
        TenantContext.setTenantId(user.getTenantId());
        CustomUserDetails principal = new CustomUserDetails(
                user.getId(), user.getTenantId(), user.getEmail(), "MEMBER",
                Set.of("USER_READ", "TENANT_READ"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
