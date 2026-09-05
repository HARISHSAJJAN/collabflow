/**
 * Task module: tasks, labels, status/priority transitions, and assignment, scoped to a
 * project. Owns the {@code tasks} and {@code task_labels} tables and the optimistic-locking
 * {@code @Version} column used to detect concurrent updates. Publishes task lifecycle events
 * ({@code TaskCreatedEvent}, {@code TaskAssignedEvent}, {@code TaskStatusChangedEvent}) that
 * the notification, audit, and websocket-broadcast listeners react to.
 */
package com.collabflow.task;
