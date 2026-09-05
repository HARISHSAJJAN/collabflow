package com.collabflow.team;

import java.util.UUID;

public record MemberRemovedEvent(UUID teamId, UUID userId, UUID removedBy) {
}
