package com.collabflow.project;

import java.util.UUID;

/** What the task module (Phase 7) needs to know about a project without touching project.internal. */
public record ProjectSummary(UUID id, UUID teamId, String name, ProjectStatus status) {
}
