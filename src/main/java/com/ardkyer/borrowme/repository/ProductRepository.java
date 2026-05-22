package com.ardkyer.borrowme.repository;

import com.ardkyer.borrowme.entity.Product;
import com.ardkyer.borrowme.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByUserOrderByCreatedAtDesc(User user);
    List<Product> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.reservations r WHERE r.user = :user")
    List<Product> findByReservationsUser(User user);

    @Query("""
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH p.hashtags
            WHERE p.title LIKE CONCAT('%', :query, '%')
               OR p.description LIKE CONCAT('%', :query, '%')
               OR u.username LIKE CONCAT('%', :query, '%')
            """)
    List<Product> searchByKeywordWithDetails(@Param("query") String query);

    @Query("""
            SELECT DISTINCT p
            FROM Product p
            JOIN p.hashtags filterTag
            LEFT JOIN FETCH p.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH p.hashtags
            WHERE filterTag.name IN :hashtags
            """)
    List<Product> findByHashtagsNameInWithDetails(@Param("hashtags") Set<String> hashtags);

    @Query("SELECT p FROM Product p ORDER BY p.createdAt DESC")
    List<Product> findRecentProducts(Pageable pageable);

    @Query("""
            SELECT p
            FROM Product p
            JOIN FETCH p.user u
            LEFT JOIN FETCH u.roles
            WHERE p.user IN :users
            ORDER BY p.createdAt DESC
            """)
    List<Product> findByUserInOrderByCreatedAtDesc(List<User> users, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.user u LEFT JOIN FETCH u.roles LEFT JOIN FETCH p.hashtags")
    List<Product> findAllWithUserAndHashtags();

    @Query("""
            SELECT DISTINCT p
            FROM Product p
            LEFT JOIN FETCH p.user u
            LEFT JOIN FETCH u.roles
            LEFT JOIN FETCH p.hashtags
            WHERE p.id = :id
            """)
    Optional<Product> findByIdWithUserAndHashtags(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.comments ORDER BY p.createdAt DESC")
    List<Product> findAllWithComments();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
