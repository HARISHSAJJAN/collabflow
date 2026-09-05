package com.collabflow.team;

import java.util.UUID;

/** What other modules (project, in Phase 6) need to display "which team does this belong to" without touching team.internal. */
public record TeamSummary(UUID id, String name) {
}
