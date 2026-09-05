/**
 * Project module: projects and project membership, scoped to a team. Owns the
 * {@code projects} and {@code project_members} tables. Publishes {@code ProjectCreatedEvent}
 * and membership-change events consumed by the notification and audit modules.
 */
package com.collabflow.project;
