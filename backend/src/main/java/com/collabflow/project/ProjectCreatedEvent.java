package com.collabflow.project;

import java.util.UUID;

public record ProjectCreatedEvent(UUID projectId, UUID teamId, String name, UUID createdBy) {
}
