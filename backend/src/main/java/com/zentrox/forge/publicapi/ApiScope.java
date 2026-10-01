package com.zentrox.forge.publicapi;

/**
 * What a public API key is allowed to do (FRD-12.2).
 *
 * Separate from {@code Permission} on purpose. Permissions are modelled for humans in roles; an API
 * key belongs to a system integration and should be scoped far more narrowly than any person. Reusing
 * the Permission enum would make it natural to mint a key with USER_DEACTIVATE or TENANT_SUSPEND,
 * which no integration needs and which turns a leaked key into an account takeover.
 *
 * Read-only by design for now - see PublicApiController.
 */
public enum ApiScope {

    /** Read workflow definitions. */
    WORKFLOWS_READ,

    /** Read workflow instances and their state. */
    INSTANCES_READ
}
