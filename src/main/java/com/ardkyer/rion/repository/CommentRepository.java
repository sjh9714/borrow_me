package com.ardkyer.rion.repository;

import com.ardkyer.rion.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 페이징 처리된 댓글 조회
    Page<Comment> findByVideoOrderByCreatedAtDesc(Video video, Pageable pageable);

    // 비디오별 댓글 수 카운트
    long countByVideo(Video video);

    // 사용자별 댓글 조회
    List<Comment> findByUser(User user);

    Optional<Comment> findFirstByVideoOrderByCreatedAtDesc(Video video);
}