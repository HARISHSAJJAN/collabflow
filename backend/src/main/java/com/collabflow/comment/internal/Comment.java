package com.collabflow.comment.internal;

import com.collabflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "edited_at")
    private Instant editedAt;

    protected Comment() {
        // JPA
    }

    public Comment(UUID taskId, UUID authorId, String body) {
        this.taskId = taskId;
        this.authorId = authorId;
        this.body = body;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    /** Sets the new body and marks this comment as edited - use this, not {@code setBody} directly, for a genuine post-creation edit. */
    public void edit(String newBody) {
        this.body = newBody;
        this.editedAt = Instant.now();
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
