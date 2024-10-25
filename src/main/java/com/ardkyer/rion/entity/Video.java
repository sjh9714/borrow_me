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

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemUnit> units = new ArrayList<>();

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
        AVAILABLE("예약가능"),
        RESERVED("예약중"),
        IN_USE("사용중"),
        OUT_OF_STOCK("재고없음");

        private final String displayName;

        ReservationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (availableQuantity == null) {
            availableQuantity = totalQuantity;
        }
        initializeItemUnits();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateReservationStatus();
    }

    private void initializeItemUnits() {
        if (units.isEmpty() && totalQuantity != null && totalQuantity > 0) {  // units를 units로 변경
            for (int i = 0; i < totalQuantity; i++) {
                ItemUnit unit = new ItemUnit();
                unit.setVideo(this);
                unit.setUnitNumber(i + 1);
                unit.setStatus(ItemStatus.AVAILABLE);
                units.add(unit);  // units를 units로 변경
            }
        }
    }

    public void updateReservationStatus() {
        if (units.isEmpty()) {
            this.reservationStatus = ReservationStatus.AVAILABLE;
            return;
        }

        long availableCount = units.stream()
                .filter(unit -> unit.getStatus() == ItemStatus.AVAILABLE)
                .count();

        if (availableCount == 0) {
            this.reservationStatus = ReservationStatus.OUT_OF_STOCK;
        } else if (availableCount == units.size()) {
            this.reservationStatus = ReservationStatus.AVAILABLE;
        } else if (units.stream().anyMatch(unit -> unit.getStatus() == ItemStatus.IN_USE)) {
            this.reservationStatus = ReservationStatus.IN_USE;
        } else {
            this.reservationStatus = ReservationStatus.RESERVED;
        }

        this.availableQuantity = (int) availableCount;
    }

    public boolean isAvailableForReservation() {
        return units.stream().anyMatch(unit -> unit.getStatus() == ItemStatus.AVAILABLE);
    }

    public String getVideoUrl() {
        return this.imageUrl;
    }

    public void setVideoUrl(String url) {
        this.imageUrl = url;
    }

    public List<ItemUnit> getAvailableUnits() {
        return units.stream()
                .filter(unit -> unit.getStatus() == ItemStatus.AVAILABLE)
                .toList();
    }

    public boolean hasAvailableUnits() {
        return getAvailableCount() > 0;
    }

    public int getAvailableCount() {
        return (int) units.stream()
                .filter(unit -> unit.getStatus() == ItemStatus.AVAILABLE)
                .count();
    }

    public void initializeUnits() {
        if (units.isEmpty() && totalQuantity != null && totalQuantity > 0) {
            for (int i = 0; i < totalQuantity; i++) {
                ItemUnit unit = new ItemUnit();
                unit.setVideo(this);
                unit.setUnitNumber(i + 1);
                unit.setStatus(ItemStatus.AVAILABLE);
                units.add(unit);
            }
        }
    }
}

