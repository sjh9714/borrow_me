package com.ardkyer.rion.controller;

import com.ardkyer.rion.entity.*;
import com.ardkyer.rion.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "Comments", description = "Comment management API")
public class CommentController {
    private final CommentService commentService;
    private final UserService userService;
    private final VideoService videoService;
    private final TemplateEngine templateEngine;

    @Autowired
    public CommentController(CommentService commentService, UserService userService, VideoService videoService, TemplateEngine templateEngine) {
        this.commentService = commentService;
        this.userService = userService;
        this.videoService = videoService;
        this.templateEngine = templateEngine;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a comment by ID", description = "Returns a single comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved comment"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<Comment> getCommentById(
            @Parameter(description = "ID of the comment to retrieve", required = true) @PathVariable Long id) {
        return commentService.getCommentById(id)
                .map(comment -> new ResponseEntity<>(comment, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/videos")
    @Operation(summary = "Get all videos with sorted comments", description = "Returns a list of videos with their comments sorted")
    public String getVideos(Model model, Authentication authentication) {
        List<Video> videos = videoService.getAllVideosWithSortedComments();
        Optional<Optional<User>> currentUser = Optional.empty();
        if (authentication != null) {
            currentUser = Optional.ofNullable(userService.getUserByUsername(authentication.getName()));
        }
        model.addAttribute("videos", videos);
        model.addAttribute("currentUser", currentUser);
        return "videos";
    }

    @GetMapping("/video/{videoId}")
    @Operation(summary = "Get comments for a video", description = "Returns a paginated list of comments for a specific video")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved comments"),
            @ApiResponse(responseCode = "404", description = "Video not found")
    })
    public ResponseEntity<Page<Comment>> getCommentsByVideo(
            @Parameter(description = "ID of the video", required = true) @PathVariable Long videoId,
            @Parameter(description = "Pageable information") Pageable pageable) {
        Video video = videoService.getVideoById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        Page<Comment> comments = commentService.getCommentsByVideo(video, pageable);
        return new ResponseEntity<>(comments, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a comment", description = "Updates an existing comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated comment"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<Comment> updateComment(
            @Parameter(description = "ID of the comment to update", required = true) @PathVariable Long id,
            @Parameter(description = "Updated comment object", required = true) @RequestBody Comment comment,
            Authentication authentication) {
        Optional<Comment> existingComment = commentService.getCommentById(id);
        if (!existingComment.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // 자신의 댓글만 수정 가능
        User currentUser = userService.findByUsername(authentication.getName());
        if (!existingComment.get().getUser().getId().equals(currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        comment.setId(id);
        Comment updatedComment = commentService.updateComment(comment);
        return new ResponseEntity<>(updatedComment, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a comment", description = "Deletes an existing comment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted comment"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "ID of the comment to delete", required = true) @PathVariable Long id,
            Authentication authentication) {
        Optional<Comment> comment = commentService.getCommentById(id);
        if (!comment.isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // 자신의 댓글만 삭제 가능
        User currentUser = userService.findByUsername(authentication.getName());
        if (!comment.get().getUser().getId().equals(currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        commentService.deleteComment(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping
    @Operation(summary = "Add a new comment", description = "Creates a new comment for a video")
    public ResponseEntity<String> addComment(
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        try {
            User user = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(Long.parseLong(payload.get("videoId")))
                    .orElseThrow(() -> new RuntimeException("Video not found"));

            Comment comment = new Comment();
            comment.setContent(payload.get("content"));
            comment.setUser(user);
            comment.setVideo(video);

            Comment savedComment = commentService.addComment(comment);

            Context context = new Context();
            context.setVariable("comment", savedComment);
            context.setVariable("currentUser", user);

            String commentHtml = templateEngine.process("fragments/comment", context);
            return ResponseEntity.ok(commentHtml);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing comment: " + e.getMessage());
        }
    }

    private String renderCommentFragment(Comment comment, User currentUser) {
        Context context = new Context();
        context.setVariable("comment", comment);
        context.setVariable("currentUser", currentUser);
        return templateEngine.process("fragments/comment :: commentItem", context);
    }
}