package com.zentrox.forge.exception;

/**
 * Thrown when {@code POST /v1/auth/register} is called while
 * {@code forge.registration.self-service-enabled} is false (the default).
 *
 * Mapped to 403 Forbidden rather than 404: the endpoint exists and the request was well-formed,
 * but this deployment does not permit the operation.
 */
public class SelfRegistrationDisabledException extends RuntimeException {

    public SelfRegistrationDisabledException(String message) {
        super(message);
    }
}
