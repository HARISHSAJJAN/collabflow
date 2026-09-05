package com.collabflow.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationType type, String payload, boolean read, Instant createdAt) {
}
