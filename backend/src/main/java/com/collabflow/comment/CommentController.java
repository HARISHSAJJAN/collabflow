package com.collabflow.comment;

import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import com.collabflow.comment.dto.CreateCommentRequest;
import com.collabflow.comment.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(@CurrentUserId UUID userId, @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse comment = commentService.createComment(userId, request.taskId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping
    public ResponseEntity<PageResponse<CommentResponse>> listComments(
            @CurrentUserId UUID userId, @RequestParam UUID taskId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(commentService.listComments(taskId, userId, pageable));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @CurrentUserId UUID userId, @PathVariable UUID commentId, @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.updateComment(commentId, userId, request.body()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@CurrentUserId UUID userId, @PathVariable UUID commentId) {
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
