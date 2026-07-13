package com.zentrox.forge.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCAFFOLD ONLY - FRD Section 11 (Configurable Dashboards).
 *
 * TODO(FRD-11.1): GET/PUT /v1/dashboards, /v1/dashboards/{id}/widgets - user-configurable
 *   widget layout persisted per tenant/user.
 */
@RestController
@RequestMapping("/v1/dashboards")
public class DashboardController {

    @GetMapping
    public ResponseEntity<Void> listDashboards() {
        // TODO(FRD-11.1): return the caller's configured dashboards.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
