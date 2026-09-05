package com.collabflow.notification;

import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> listMyNotifications(
            @CurrentUserId UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.listMyNotifications(userId, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@CurrentUserId UUID userId, @PathVariable UUID notificationId) {
        notificationService.markRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@CurrentUserId UUID userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
