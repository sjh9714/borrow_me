package com.ardkyer.rion.entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "videos")
@Getter @Setter
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "video_url", nullable = false)
    private String imageUrl;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "available_quantity")
    private Integer availableQuantity;

    @Column(name = "reservation_status")
    @Enumerated(EnumType.STRING)
    private ReservationStatus reservationStatus = ReservationStatus.AVAILABLE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonManagedReference
    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Reservation> reservations = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "video_hashtags",
            joinColumns = @JoinColumn(name = "video_id"),
            inverseJoinColumns = @JoinColumn(name = "hashtag_id")
    )
    private Set<Hashtag> hashtags = new HashSet<>();

    @Transient
    private boolean followedByCurrentUser;

    public enum ReservationStatus {
        AVAILABLE,       // 예약 가능
        RESERVED,       // 예약중
        OUT_OF_STOCK    // 재고 없음
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (availableQuantity == null) {
            availableQuantity = totalQuantity;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateReservationStatus();
    }

    private void updateReservationStatus() {
        if (availableQuantity == 0) {
            this.reservationStatus = ReservationStatus.OUT_OF_STOCK;
        } else if (!reservations.isEmpty()) {
            this.reservationStatus = ReservationStatus.RESERVED;
        } else {
            this.reservationStatus = ReservationStatus.AVAILABLE;
        }
    }

    public boolean isAvailableForReservation() {
        return availableQuantity > 0 && reservationStatus != ReservationStatus.OUT_OF_STOCK;
    }

    // For backwards compatibility
    public String getVideoUrl() {
        return this.imageUrl;
    }

    public void setVideoUrl(String url) {
        this.imageUrl = url;
    }
}