package com.ardkyer.rion.controller;

import com.ardkyer.rion.entity.*;
import com.ardkyer.rion.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "Comments", description = "Comment management API")
public class CommentController {
    private final CommentService commentService;
    private final UserService userService;
    private final VideoService videoService;

    @Autowired
    public CommentController(CommentService commentService, UserService userService, VideoService videoService) {
        this.commentService = commentService;
        this.userService = userService;
        this.videoService = videoService;
    }

    @Getter @Setter
    public static class CommentRequest {
        private Long videoId;
        private String content;
    }

    @Getter @Setter
    public static class CommentResponse {
        private Long id;
        private String content;
        private String username;
        private Date createdAt;
        private int likeCount;

        public CommentResponse(Comment comment) {
            this.id = comment.getId();
            this.content = comment.getContent();
            this.username = comment.getUser().getUsername();
            this.createdAt = java.sql.Timestamp.valueOf(comment.getCreatedAt());
            this.likeCount = comment.getLikeCount();
        }
    }

    @PostMapping
    @Operation(summary = "Add a new comment", description = "Creates a new comment for a video")
    public ResponseEntity<?> addComment(@RequestBody CommentRequest request, Authentication authentication) {
        try {
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Comment content cannot be empty"
                ));
            }

            User user = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(request.getVideoId())
                    .orElseThrow(() -> new IllegalArgumentException("Video not found"));

            Comment comment = new Comment();
            comment.setContent(request.getContent().trim());
            comment.setUser(user);
            comment.setVideo(video);
            comment.setLikeCount(0);

            Comment savedComment = commentService.addComment(comment);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "comment", new CommentResponse(savedComment)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error processing comment"
            ));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a comment by ID")
    public ResponseEntity<?> getCommentById(@PathVariable Long id) {
        return commentService.getCommentById(id)
                .map(comment -> ResponseEntity.ok(new CommentResponse(comment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/video/{videoId}")
    @Operation(summary = "Get comments for a video")
    public ResponseEntity<?> getCommentsByVideo(
            @PathVariable Long videoId,
            Pageable pageable) {
        try {
            Video video = videoService.getVideoById(videoId)
                    .orElseThrow(() -> new IllegalArgumentException("Video not found"));

            Page<Comment> comments = commentService.getCommentsByVideo(video, pageable);
            Page<CommentResponse> commentResponses = comments.map(CommentResponse::new);

            return ResponseEntity.ok(commentResponses);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a comment")
    public ResponseEntity<?> updateComment(
            @PathVariable Long id,
            @RequestBody CommentRequest request,
            Authentication authentication) {
        try {
            Comment existingComment = commentService.getCommentById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

            User currentUser = userService.findByUsername(authentication.getName());
            if (!existingComment.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "You can only update your own comments"
                ));
            }

            existingComment.setContent(request.getContent().trim());
            Comment updatedComment = commentService.updateComment(existingComment);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "comment", new CommentResponse(updatedComment)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a comment")
    public ResponseEntity<?> deleteComment(@PathVariable Long id, Authentication authentication) {
        try {
            Comment comment = commentService.getCommentById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

            User currentUser = userService.findByUsername(authentication.getName());
            if (!comment.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "You can only delete your own comments"
                ));
            }

            commentService.deleteComment(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}