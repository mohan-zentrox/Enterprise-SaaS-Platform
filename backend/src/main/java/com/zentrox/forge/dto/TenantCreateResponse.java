package com.zentrox.forge.dto;

import java.util.UUID;

public record TenantCreateResponse(TenantResponse tenant, UUID ownerUserId, String ownerEmail) {
}
