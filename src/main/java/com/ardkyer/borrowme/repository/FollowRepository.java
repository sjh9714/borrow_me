package com.ardkyer.borrowme.repository;

import com.ardkyer.borrowme.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowed(User follower, User followed);
    boolean existsByFollowerAndFollowed(User follower, User followed);
    List<Follow> findByFollower(User follower);
    List<Follow> findByFollowed(User followed);
    long countByFollower(User follower);
    long countByFollowed(User followed);

    // 배치 팔로우 체크: 한 사용자가 여러 사용자를 팔로우하는지 일괄 조회
    List<Follow> findByFollowerAndFollowedIn(User follower, List<User> followed);

    @Query("SELECT f.followed.id FROM Follow f WHERE f.follower = :follower AND f.followed IN :followed")
    List<Long> findFollowedIdsByFollowerAndFollowedIn(
            @Param("follower") User follower,
            @Param("followed") List<User> followed);
}
