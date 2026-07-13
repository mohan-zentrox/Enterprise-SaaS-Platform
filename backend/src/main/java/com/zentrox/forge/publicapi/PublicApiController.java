package com.zentrox.forge.publicapi;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCAFFOLD ONLY - FRD Section 12 (Public API-Key-Authenticated Endpoints).
 *
 * Mounted under /v1/public/**, which SecurityConfig currently permitAll's; once
 * {@link ApiKeyAuthenticationFilter} is implemented (FRD-12.2) it becomes the real
 * gate for this prefix instead of Spring Security's JWT filter.
 *
 * TODO(FRD-12.5): Expose a deliberately narrow read-mostly surface here (e.g. workflow
 *   instance status lookups for external system integration) - do not simply re-expose
 *   the full /v1 surface under a different auth scheme.
 */
@RestController
@RequestMapping("/v1/public")
public class PublicApiController {

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        // TODO(FRD-12.5): replace with a real API-key-scoped endpoint.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
