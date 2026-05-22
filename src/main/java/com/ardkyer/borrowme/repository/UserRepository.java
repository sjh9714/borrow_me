package com.ardkyer.borrowme.repository;

import com.ardkyer.borrowme.entity.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u ORDER BY SIZE(u.followers) DESC")
    List<User> findTopUsersByFollowerCount(Pageable pageable);

    default List<User> findTopUsersByFollowerCount(int limit) {
        return findTopUsersByFollowerCount(PageRequest.of(0, limit));
    }
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
