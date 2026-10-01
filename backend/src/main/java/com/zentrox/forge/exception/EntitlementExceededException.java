package com.zentrox.forge.exception;

/**
 * The tenant's plan does not permit this operation - either a usage limit is reached or the
 * subscription is not in a state that allows writes.
 *
 * Mapped to 402 Payment Required, not 403. The distinction matters to the caller: 403 says "you
 * personally may not do this", which no amount of paying will change; 402 says "this organization
 * needs a different plan", which is actionable and is the whole point of surfacing it.
 */
public class EntitlementExceededException extends RuntimeException {

    public EntitlementExceededException(String message) {
        super(message);
    }
}
