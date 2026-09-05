package com.collabflow.team;

import java.util.UUID;

public record MemberAddedEvent(UUID teamId, UUID userId, UUID addedBy) {
}
