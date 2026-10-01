package com.zentrox.forge.notification;

import com.zentrox.forge.dto.PageResponse;
import com.zentrox.forge.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * FRD Section 10 - the caller's own notification inbox.
 *
 * There is intentionally no {@code @PreAuthorize} here and no permission in the catalog for it.
 * Every authenticated user can read their own inbox and nobody can read anyone else's: the
 * recipient is taken from the access token, not from a parameter (see NotificationService). A
 * permission would imply the opposite - that some role could be granted access to other people's
 * notifications.
 */
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ResponseEntity.ok(notificationService.listInbox(PageRequest.of(safePage, safeSize)));
    }

    /** Cheap enough to poll for an unread badge without paging the whole inbox. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("markedRead", notificationService.markAllRead()));
    }
}
