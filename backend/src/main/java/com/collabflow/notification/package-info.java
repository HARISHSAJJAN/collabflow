/**
 * Notification module: in-app notifications with read/unread state. Owns the
 * {@code notifications} table. Consumes domain events from other modules (task assignment,
 * comments, project invitations) via Kafka listeners and, on write, pushes the new
 * notification to the recipient's WebSocket session.
 */
package com.collabflow.notification;
