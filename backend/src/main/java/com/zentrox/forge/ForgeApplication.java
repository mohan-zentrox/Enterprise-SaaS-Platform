package com.zentrox.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Project Forge - Multi-tenant Enterprise SaaS Platform.
 *
 * Entry point. See docs/ARCHITECTURE.md for the module layout:
 * tenancy (tenant resolution) -> security (JWT/RBAC) -> service -> repository (tenant-scoped) -> entity.
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class ForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeApplication.class, args);
    }
}
