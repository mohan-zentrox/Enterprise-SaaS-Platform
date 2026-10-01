package com.zentrox.forge.sso;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps identity-provider group claims onto Forge role names (FRD-13.3).
 *
 * <p><b>Configurable per tenant, with no 1:1 assumption.</b> The naive implementation treats the
 * IdP group name as the Forge role name, which breaks the moment a customer's directory calls its
 * administrators "IT-Admins-EMEA" - and worse, silently grants nothing (or the wrong thing) rather
 * than failing visibly. The mapping is therefore explicit data: {@code {"IT-Admins-EMEA": "ADMIN"}}.
 *
 * <p><b>Most-privileged wins on multiple matches.</b> A user in both a mapped ADMIN group and a
 * mapped MEMBER group gets ADMIN. Order of claims from an IdP is not meaningful, so "first match"
 * would make the result depend on directory internals; and taking the least privilege would lock
 * legitimate administrators out whenever they are also in a general staff group, which is almost
 * always.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdpRoleMapper {

    /**
     * Highest privilege first. Anything not listed (a custom role) ranks below the system roles,
     * because we cannot know how privileged a tenant's custom role is.
     */
    private static final List<String> PRIVILEGE_ORDER = List.of("OWNER", "ADMIN", "MEMBER");

    private final ObjectMapper objectMapper;

    /**
     * @param roleMappingJson per-tenant map of IdP group name to Forge role name
     * @param idpGroups       groups asserted by the IdP for this user
     * @param defaultRole     used when nothing matches
     */
    public String resolveRole(String roleMappingJson, List<String> idpGroups, String defaultRole) {
        Map<String, String> mapping = parseMapping(roleMappingJson);
        if (mapping.isEmpty() || idpGroups == null || idpGroups.isEmpty()) {
            return defaultRole;
        }

        String best = null;
        for (String group : idpGroups) {
            String mapped = mapping.get(group);
            if (mapped == null) {
                continue;
            }
            best = (best == null) ? mapped : morePrivileged(best, mapped);
        }

        return best == null ? defaultRole : best;
    }

    private String morePrivileged(String a, String b) {
        int rankA = PRIVILEGE_ORDER.indexOf(a);
        int rankB = PRIVILEGE_ORDER.indexOf(b);
        // indexOf returns -1 for a custom role; treat that as lowest priority rather than highest,
        // so an unrecognised mapping can never outrank an explicit system role.
        if (rankA < 0) {
            return rankB < 0 ? a : b;
        }
        if (rankB < 0) {
            return a;
        }
        return rankA <= rankB ? a : b;
    }

    private Map<String, String> parseMapping(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            // A malformed mapping must not grant a role by accident - fall back to the default,
            // and make the misconfiguration visible.
            log.error("Ignoring unparseable SSO role mapping: {}", e.getMessage());
            return Map.of();
        }
    }
}
