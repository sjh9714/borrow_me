package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.*;
import com.ardkyer.rion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    public CommentServiceImpl(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Override
    @Transactional
    public Comment addComment(Comment comment) {
        Comment savedComment = commentRepository.save(comment);

        if (savedComment.getVideo() != null) {
            System.out.println("비디오 ID: " + savedComment.getVideo().getId());
        } else {
            System.out.println("비디오가 null입니다.");
        }

        // 자신의 게시물이 아닐 경우에만 알림 생성
        if (!savedComment.getVideo().getUser().equals(savedComment.getUser())) {
            Notification notification = new Notification();
            notification.setUser(savedComment.getVideo().getUser());
            notification.setType(NotificationType.COMMENT);  // 알림 타입 설정
            notification.setVideo(savedComment.getVideo());  // video 엔티티 직접 설정
            notification.setComment(savedComment);  // comment 엔티티 설정
            notification.setPostTitle(savedComment.getVideo().getTitle());
            notification.setCommenterName(savedComment.getUser().getUsername());
            notification.setCommentContent(savedComment.getContent());
            notification.setMessage(String.format("[%s] %s님이 댓글을 달았습니다: %s",
                    savedComment.getVideo().getTitle(),
                    savedComment.getUser().getUsername(),
                    savedComment.getContent()));

            notificationRepository.save(notification);
        }

        return savedComment;
    }

    @Override
    public Optional<Comment> getCommentById(Long id) {
        return commentRepository.findById(id);
    }

    @Override
    public Page<Comment> getCommentsByVideo(Video video, Pageable pageable) {
        return commentRepository.findByVideoOrderByCreatedAtDesc(video, pageable);
    }

    @Override
    @Transactional
    public Comment updateComment(Comment comment) {
        return commentRepository.save(comment);
    }

    @Override
    @Transactional  // 메서드 레벨에도 @Transactional 추가
    public void deleteComment(Long id) {
        try {
            // 1. 해당 댓글과 관련된 알림 삭제
            notificationRepository.deleteAllByParentCommentId(id);  // 답글 관련 알림 먼저 삭제
            notificationRepository.deleteAllByCommentId(id);        // 댓글 관련 알림 삭제

            // 2. 댓글 삭제 (cascade로 인해 답글도 자동 삭제됨)
            commentRepository.deleteById(id);

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete comment: " + e.getMessage());
        }
    }

    @Override
    public long getCommentCountForVideo(Video video) {
        return commentRepository.countByVideo(video);
    }

    @Override
    public Optional<Comment> getRecentCommentForVideo(Video video) {
        return commentRepository.findFirstByVideoOrderByCreatedAtDesc(video);
    }

    @Override
    public List<Comment> getCommentsByUser(User user) {
        return commentRepository.findByUser(user);
    }
}