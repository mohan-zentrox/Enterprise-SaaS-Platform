package com.zentrox.forge.exception;

import com.zentrox.forge.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTransitionException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ErrorResponse body = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
                "Request payload failed validation", request.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * A body Jackson could not bind: malformed JSON, a string where a number belongs, or - the
     * common case here - an unrecognised {@code Permission} enum name in a role payload.
     *
     * Handled explicitly rather than via the {@code ErrorResponse} check in
     * {@link #handleUnexpected}, because unlike most Spring MVC exceptions
     * {@code HttpMessageNotReadableException} does NOT implement that interface, so the catch-all
     * would otherwise report a client mistake as a 500.
     *
     * The exception message is not echoed: Jackson's text names the target class, field and
     * accepted enum constants, which is internal shape the caller has no business seeing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        log.debug("Unreadable request body for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "The request body could not be read. Check the JSON shape, field types, and any enum values.",
                request);
    }

    /** A path variable or query parameter that could not be converted - e.g. a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", request);
    }

    /**
     * Two callers transitioned the same workflow instance at once and this one lost the optimistic
     * lock (see WorkflowInstance#lockVersion). 409 is the honest answer: the request was valid, the
     * state moved underneath it, and retrying against the new state is the correct client response.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "This record was modified by another request. Re-read it and retry.", request);
    }

    /**
     * 403 like AccessDeniedException, but the message is kept: see PrivilegeEscalationException for
     * why this case should be specific where the generic authorization failure should not.
     */
    @ExceptionHandler(PrivilegeEscalationException.class)
    public ResponseEntity<ErrorResponse> handlePrivilegeEscalation(PrivilegeEscalationException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * 402 rather than 403: a plan limit is the organization's to change, not the caller's
     * authority. See EntitlementExceededException.
     */
    @ExceptionHandler(EntitlementExceededException.class)
    public ResponseEntity<ErrorResponse> handleEntitlementExceeded(EntitlementExceededException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request);
    }

    @ExceptionHandler(SelfRegistrationDisabledException.class)
    public ResponseEntity<ErrorResponse> handleSelfRegistrationDisabled(SelfRegistrationDisabledException ex,
                                                                          HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * An IllegalStateException here is always a server-side invariant failure - a corrupt
     * definition_json, an unbound TenantContext, a missing seeded role. It was previously mapped to
     * 400, which told the caller to fix a request they had got right and leaked internal detail
     * (e.g. "Corrupt definition_json for workflow &lt;id&gt;") into the response body.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.error("Invariant violation handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred", request);
    }

    /**
     * Catch-all, so an unmapped exception still returns the documented ErrorResponse shape (see
     * docs/API.md) instead of Spring's default error representation.
     *
     * Spring MVC's own exceptions - unreadable request body, wrong HTTP method, unsupported media
     * type, unparseable path variable, unknown path - all implement Spring's
     * {@code org.springframework.web.ErrorResponse} (fully qualified below: the simple name
     * collides with our own {@link ErrorResponse} DTO) and already carry the status they should
     * produce. They must be honoured before falling through,
     * otherwise this handler turns every one of them into a 500: malformed JSON would stop being a
     * 400, and a typo in a URL would stop being a 404.
     *
     * For client errors the message is derived from the status, never from the exception, because
     * these messages leak internals freely (Jackson names the target class and field, and the
     * no-handler message echoes the raw path). Server errors are logged in full and the client is
     * told nothing.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            if (status.is4xxClientError()) {
                log.debug("Rejecting {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
                return build(status, clientMessageFor(status), request);
            }
        }

        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred", request);
    }

    private String clientMessageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request could not be read. Check the payload shape and field types.";
            case METHOD_NOT_ALLOWED -> "That HTTP method is not supported on this endpoint";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type; this API accepts application/json";
            case NOT_ACCEPTABLE -> "This endpoint cannot produce any of the requested content types";
            case NOT_FOUND -> "No such endpoint";
            default -> status.getReasonPhrase();
        };
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
