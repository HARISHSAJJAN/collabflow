package com.collabflow.comment.internal;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByTaskIdOrderByCreatedAt(UUID taskId, Pageable pageable);
}
