package com.zentrox.forge.publicapi.dto;

/**
 * Returned once, from key creation only.
 *
 * {@code warning} is part of the payload rather than left to documentation because this is the sole
 * moment the secret exists - a client that does not store it now cannot recover it, and saying so
 * in the response is the most likely place anyone will read it.
 */
public record ApiKeyCreatedResponse(ApiKeyResponse key, String secret, String warning) {

    public static ApiKeyCreatedResponse of(ApiKeyResponse key, String secret) {
        return new ApiKeyCreatedResponse(key, secret,
                "Store this key now. It is hashed before storage and cannot be shown again. "
                        + "If you lose it, revoke this key and create another.");
    }
}
