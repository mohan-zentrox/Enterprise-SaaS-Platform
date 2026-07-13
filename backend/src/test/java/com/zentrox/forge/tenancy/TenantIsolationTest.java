package com.zentrox.forge.tenancy;

import com.zentrox.forge.dto.workflow.WorkflowDefinitionResponse;
import com.zentrox.forge.entity.Permission;
import com.zentrox.forge.entity.Role;
import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.entity.User;
import com.zentrox.forge.entity.UserStatus;
import com.zentrox.forge.entity.WorkflowDefinition;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.repository.TenantRepository;
import com.zentrox.forge.repository.tenant.RoleRepository;
import com.zentrox.forge.repository.tenant.UserRepository;
import com.zentrox.forge.repository.tenant.WorkflowDefinitionRepository;
import com.zentrox.forge.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cross-tenant-isolation test required by FRD Section 3 (Multi-Tenancy): proves
 * tenant A cannot read tenant B's workflow definitions, at BOTH the repository layer
 * (the structural guardrail in TenantScopedRepositoryImpl) and the service layer
 * (WorkflowService, which is what every controller actually calls).
 *
 * Runs against the full Spring context (test profile -> in-memory H2, see
 * application-test.yml) so the exact repositoryBaseClass wiring used in production
 * (see config.TenantScopedRepositoryConfig) is exercised, not a hand-rolled substitute.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantIsolationTest {

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired
    private WorkflowService workflowService;

    private Tenant tenantA;
    private Tenant tenantB;
    private WorkflowDefinition tenantBsWorkflow;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(newTenant("tenant-a"));
        tenantB = tenantRepository.save(newTenant("tenant-b"));

        Role ownerRoleB = roleRepository.save(newOwnerRole(tenantB.getId()));
        User userB = userRepository.save(newUser(tenantB.getId(), "owner@tenant-b.example", ownerRoleB));

        tenantBsWorkflow = workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .tenantId(tenantB.getId())
                .name("tenant-b-secret-workflow")
                .description("Should never be visible to tenant A")
                .definitionJson("{\"states\":[\"DRAFT\",\"DONE\"],\"transitions\":[{\"from\":\"DRAFT\",\"to\":\"DONE\"}]}")
                .version(1)
                .createdBy(userB.getId())
                .build());
    }

    @Test
    void repositoryLayer_findByIdAndTenantId_doesNotReturnAnotherTenantsRow() {
        Optional<WorkflowDefinition> result =
                workflowDefinitionRepository.findByIdAndTenantId(tenantBsWorkflow.getId(), tenantA.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void repositoryLayer_findByIdAndTenantId_returnsTheRowForItsOwnTenant() {
        Optional<WorkflowDefinition> result =
                workflowDefinitionRepository.findByIdAndTenantId(tenantBsWorkflow.getId(), tenantB.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("tenant-b-secret-workflow");
    }

    @Test
    void repositoryLayer_listByTenant_neverIncludesAnotherTenantsWorkflow() {
        workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .tenantId(tenantA.getId())
                .name("tenant-a-own-workflow")
                .definitionJson("{\"states\":[\"DRAFT\"],\"transitions\":[]}")
                .version(1)
                .createdBy(java.util.UUID.randomUUID())
                .build());

        List<WorkflowDefinition> tenantAWorkflows = workflowDefinitionRepository.findAllByTenantId(tenantA.getId());

        assertThat(tenantAWorkflows).extracting(WorkflowDefinition::getId)
                .doesNotContain(tenantBsWorkflow.getId());
    }

    @Test
    void repositoryLayer_tenantUnawareFindById_isStructurallyDisabled() {
        // Even a developer who "forgets" to filter by tenant cannot leak data this way:
        // the base repository (TenantScopedRepositoryImpl) throws instead of falling
        // back to an unscoped lookup.
        assertThatThrownBy(() -> workflowDefinitionRepository.findById(tenantBsWorkflow.getId()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serviceLayer_tenantACannotFetchTenantBsWorkflowByGuessingItsId() {
        TenantContext.setTenantId(tenantA.getId());
        try {
            assertThatThrownBy(() -> workflowService.getDefinition(tenantBsWorkflow.getId()))
                    .isInstanceOf(NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void serviceLayer_tenantBCanFetchItsOwnWorkflow() {
        TenantContext.setTenantId(tenantB.getId());
        try {
            WorkflowDefinitionResponse response = workflowService.getDefinition(tenantBsWorkflow.getId());
            assertThat(response.id()).isEqualTo(tenantBsWorkflow.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void serviceLayer_tenantAsWorkflowListNeverContainsTenantBsWorkflows() {
        TenantContext.setTenantId(tenantA.getId());
        try {
            List<WorkflowDefinitionResponse> definitions = workflowService.listDefinitions();
            assertThat(definitions).extracting(WorkflowDefinitionResponse::id)
                    .doesNotContain(tenantBsWorkflow.getId());
        } finally {
            TenantContext.clear();
        }
    }

    private Tenant newTenant(String slug) {
        return Tenant.builder()
                .name(slug + "-name")
                .slug(slug)
                .status(TenantStatus.ACTIVE)
                .build();
    }

    private Role newOwnerRole(java.util.UUID tenantId) {
        return Role.builder()
                .tenantId(tenantId)
                .name("OWNER")
                .systemRole(true)
                .permissions(EnumSet.allOf(Permission.class))
                .build();
    }

    private User newUser(java.util.UUID tenantId, String email, Role role) {
        return User.builder()
                .tenantId(tenantId)
                .email(email)
                .passwordHash("test-hash-not-used-in-this-test")
                .fullName("Owner")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
