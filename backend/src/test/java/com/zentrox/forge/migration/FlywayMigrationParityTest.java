package com.zentrox.forge.migration;

import com.zentrox.forge.entity.Tenant;
import com.zentrox.forge.entity.TenantStatus;
import com.zentrox.forge.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the gap between the two database configurations this application has.
 *
 * The fast suite runs on H2 with {@code ddl-auto: create-drop} and {@code flyway.enabled: false} -
 * Hibernate generates the schema from the entities, so {@code db/migration/*.sql} is never
 * executed and never checked. Production does the reverse: Flyway applies the migrations and
 * Hibernate runs {@code ddl-auto: validate} against the result. Any column an entity declares but
 * a migration forgot (or vice versa) is therefore invisible in CI and fatal at deploy time.
 *
 * This test runs the real thing: real Postgres, real Flyway migrations, real {@code validate}. If
 * the context starts, the migrations and the entity mappings agree. If someone adds a field
 * without a migration, this is the test that fails.
 *
 * <p><b>Requires a reachable Docker daemon.</b> GitHub's Linux runners have one, so CI covers it.
 * It does NOT work when Maven is itself run inside a container against Docker Desktop for Windows:
 * the shared {@code /var/run/docker.sock} is a named-pipe proxy that answers {@code /info} with
 * HTTP 400, and Testcontainers reports "Could not find a valid Docker environment". Run the build
 * on the host in that situation, or verify the same property end-to-end with
 * {@code docker compose up --build} - the application boots with Flyway on and
 * {@code ddl-auto: validate}, so a successful startup proves the same parity.
 */
@SpringBootTest
@Testcontainers
class FlywayMigrationParityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    /**
     * Deliberately NOT the "test" profile: this test needs the production database configuration
     * (Flyway on, ddl-auto=validate) with only the datasource swapped for the container. Redis is
     * not contacted - Spring Data Redis creates its connection factory lazily and nothing here
     * issues a refresh-token call.
     */
    @DynamicPropertySource
    static void productionLikeDatabaseConfig(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("forge.jwt.secret", () -> "migration-parity-test-secret-at-least-32-bytes-long");
    }

    @Test
    void flywaySchemaSatisfiesHibernateValidationAndAcceptsWrites() {
        // Reaching this point already proves ddl-auto=validate passed against the migrated schema,
        // since a mismatch fails context startup. Writing a row additionally proves the Postgres
        // defaults the migration relies on (gen_random_uuid via pgcrypto, now()) actually work.
        Tenant saved = tenantRepository.save(Tenant.builder()
                .name("Migration Parity")
                .slug("migration-parity")
                .status(TenantStatus.ACTIVE)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(tenantRepository.findBySlug("migration-parity")).isPresent();
    }
}
