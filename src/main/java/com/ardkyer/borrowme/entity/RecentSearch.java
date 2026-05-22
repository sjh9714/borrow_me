package com.ardkyer.borrowme.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "recent_search",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recent_search_user_keyword",
                columnNames = {"user_id", "keyword"}
        )
)
@Getter
@Setter
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "search_time", nullable = false)
    private LocalDateTime searchTime = LocalDateTime.now();
}
