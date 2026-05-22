package com.ardkyer.borrowme.repository;

import com.ardkyer.borrowme.entity.RecentSearch;
import com.ardkyer.borrowme.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {
    List<RecentSearch> findByUserOrderBySearchTimeDesc(User user);

    @Modifying
    @Query(value = """
            INSERT INTO recent_search (user_id, keyword, search_time)
            VALUES (:userId, :keyword, :searchTime)
            ON DUPLICATE KEY UPDATE search_time = VALUES(search_time)
            """, nativeQuery = true)
    void upsertByUserIdAndKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("searchTime") LocalDateTime searchTime
    );

    void deleteByUserAndKeyword(User user, String keyword);
    void deleteAllByUser(User user);
}
