package com.ardkyer.rion.repository;

import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    // 기본 조회
    List<Video> findByUserOrderByCreatedAtDesc(User user);
    List<Video> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // 예약 관련 조회
    @Query("SELECT v FROM Video v JOIN v.reservations r WHERE r.user = :user")
    List<Video> findByReservationsUser(User user);

    // 검색 관련
    List<Video> findByTitleContainingOrDescriptionContaining(String title, String description);

    // 해시태그 검색
    @Query("SELECT v FROM Video v JOIN v.hashtags h WHERE h.name IN :hashtags")
    List<Video> findByHashtagsNameIn(Set<String> hashtags);

    // 최근 게시물
    @Query("SELECT v FROM Video v ORDER BY v.createdAt DESC")
    List<Video> findRecentVideos(Pageable pageable);
}