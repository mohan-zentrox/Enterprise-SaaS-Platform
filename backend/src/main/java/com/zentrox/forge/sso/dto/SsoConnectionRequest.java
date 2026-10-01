package com.zentrox.forge.sso.dto;

import com.zentrox.forge.sso.SsoProtocol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code clientSecret} is write-only: send it to set or rotate it, omit it to leave the stored value
 * untouched. It is never echoed back by any response (see SsoConnectionResponse).
 */
public record SsoConnectionRequest(

        @NotNull
        SsoProtocol protocol,

        boolean enabled,

        @Size(max = 500)
        String issuer,

        @Size(max = 255)
        String clientId,

        @Size(max = 1000)
        String clientSecret,

        @Size(max = 500)
        String authorizationEndpoint,

        @Size(max = 500)
        String tokenEndpoint,

        @Size(max = 500)
        String jwksUri,

        @Size(max = 100)
        String emailClaim,

        @Size(max = 100)
        String groupsClaim,

        String roleMappingJson,

        boolean autoProvision,

        @Size(max = 100)
        String defaultRole
) {
}
