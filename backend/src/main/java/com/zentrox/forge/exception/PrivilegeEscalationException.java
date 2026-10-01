package com.zentrox.forge.exception;

/**
 * Thrown when a caller tries to grant a role a permission they do not hold themselves
 * (see RoleManagementService).
 *
 * Distinct from Spring's {@code AccessDeniedException} on purpose. That one is mapped to a
 * deliberately vague "You do not have permission to perform this action", because telling an
 * attacker exactly which permission gates an endpoint is a small information leak. Here the
 * opposite is true: the caller passed the endpoint's permission check, and the specific list of
 * permissions they over-reached on is the only thing that makes the 403 actionable - without it an
 * administrator building a custom role just sees an unexplained refusal.
 */
public class PrivilegeEscalationException extends RuntimeException {

    public PrivilegeEscalationException(String message) {
        super(message);
    }
}
