package com.zentrox.forge.dashboard;

import com.zentrox.forge.dashboard.dto.DashboardRequest;
import com.zentrox.forge.dashboard.dto.DashboardResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * FRD Section 11 - configurable dashboards.
 *
 * <p>No dashboard-specific permission exists in the catalog, deliberately. A dashboard is a saved
 * view over data the caller can already see - the widgets read through the same tenant-scoped
 * repositories as every other endpoint, so a dashboard cannot reveal anything its viewer could not
 * otherwise reach. Inventing a DASHBOARD_READ permission would imply dashboards are a separate
 * data-access path, which is exactly the design this avoids.
 *
 * <p>The one exception is {@code TENANT_UPDATE} on shared dashboards: those are visible to the whole
 * organization, which makes them tenant configuration rather than a personal view.
 */
@RestController
@RequestMapping("/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<List<DashboardResponse>> listDashboards() {
        return ResponseEntity.ok(dashboardService.listDashboards());
    }

    /** Widget data is resolved here, not in the list view. */
    @GetMapping("/{id}")
    public ResponseEntity<DashboardResponse> getDashboard(@PathVariable UUID id) {
        return ResponseEntity.ok(dashboardService.getDashboard(id));
    }

    /**
     * Creating a dashboard shared with the whole tenant needs TENANT_UPDATE; a private one needs
     * nothing beyond being authenticated. Expressed with {@code #request.shared()} so the single
     * endpoint enforces both rules rather than splitting into two paths that could drift.
     */
    @PostMapping
    @PreAuthorize("!#request.shared() or hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<DashboardResponse> createDashboard(@Valid @RequestBody DashboardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.createDashboard(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("!#request.shared() or hasAuthority('TENANT_UPDATE')")
    public ResponseEntity<DashboardResponse> updateDashboard(@PathVariable UUID id,
                                                              @Valid @RequestBody DashboardRequest request) {
        return ResponseEntity.ok(dashboardService.updateDashboard(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDashboard(@PathVariable UUID id) {
        dashboardService.deleteDashboard(id);
        return ResponseEntity.noContent().build();
    }
}
