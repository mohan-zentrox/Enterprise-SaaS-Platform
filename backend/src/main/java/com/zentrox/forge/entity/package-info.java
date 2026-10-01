/**
 * Entity package.
 *
 * The {@code tenantFilter} definition lives here, at package level, because Hibernate permits
 * exactly ONE {@code @FilterDef} per filter name across the whole persistence unit - declaring it
 * on each entity (as was originally done) fails startup with
 * "Multiple '@FilterDef' annotations define a filter named 'tenantFilter'".
 *
 * Each tenant-scoped entity still carries its own {@code @Filter(name = "tenantFilter",
 * condition = "tenant_id = :tenantId")} - that is the per-entity application of this single
 * definition, and it is enabled per-request by
 * {@link com.zentrox.forge.tenancy.TenantFilterInterceptor}.
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
package com.zentrox.forge.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;
