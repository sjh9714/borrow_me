package com.ardkyer.rion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter @Setter
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "contact")
    private String contact;  // 연락처 정보

    @Column(name = "rental_start_date")
    private LocalDateTime rentalStartDate;  // 대여 시작일

    @Column(name = "rental_end_date")
    private LocalDateTime rentalEndDate;    // 대여 종료일

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;    // 예약 메모나 특이사항

    public enum ReservationStatus {
        PENDING,    // 예약 대기중
        CONFIRMED,  // 예약 확정
        CANCELED,   // 예약 취소됨
        COMPLETED,  // 대여 완료
        RETURNED    // 반납 완료
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 예약 가능 여부 확인 메서드
    public boolean isActive() {
        return status == ReservationStatus.PENDING ||
                status == ReservationStatus.CONFIRMED;
    }

    // 예약 취소 가능 여부 확인 메서드
    public boolean isCancelable() {
        return status == ReservationStatus.PENDING ||
                status == ReservationStatus.CONFIRMED;
    }

    // 대여 기간 유효성 검사 메서드
    public boolean isValidRentalPeriod() {
        if (rentalStartDate == null || rentalEndDate == null) {
            return false;
        }
        return !rentalStartDate.isAfter(rentalEndDate) &&
                !rentalStartDate.isBefore(LocalDateTime.now());
    }

    // 남은 대여 일수 계산 메서드
    public long getRemainingDays() {
        if (rentalEndDate == null || status != ReservationStatus.CONFIRMED) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(now, rentalEndDate).toDays();
    }
}