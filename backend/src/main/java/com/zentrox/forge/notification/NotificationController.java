package com.zentrox.forge.notification;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCAFFOLD ONLY - FRD Section 10 (Notifications).
 *
 * TODO(FRD-10.1): GET /v1/notifications (paginated inbox, unread-first),
 *   POST /v1/notifications/{id}/read, POST /v1/notifications/read-all.
 */
@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    @GetMapping
    public ResponseEntity<Void> listNotifications() {
        // TODO(FRD-10.1): return the caller's paginated in-app notification inbox.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
