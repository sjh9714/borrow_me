package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.Comment;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CommentService {
    Comment addComment(Comment comment);
    Optional<Comment> getCommentById(Long id);
    Page<Comment> getCommentsByVideo(Video video, Pageable pageable);
    Comment updateComment(Comment comment);
    void deleteComment(Long id);
    long getCommentCountForVideo(Video video);
    Optional<Comment> getRecentCommentForVideo(Video video);
    List<Comment> getCommentsByUser(User user);
}