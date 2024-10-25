
package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ReservationService {
    // 예약 생성
    Reservation reserve(Video video, User user, int quantity);

    // 예약 취소
    void cancelReservation(Long reservationId);

    // 사용자별 예약 조회
    List<Reservation> getUserReservations(User user);

    // 상품별 예약 조회
    List<Reservation> getVideoReservations(Video video);

    // 특정 사용자의 특정 상품 예약 조회
    Optional<Reservation> getReservationByVideoAndUser(Video video, User user);

    // 예약 상태 변경
    Reservation updateReservationStatus(Long reservationId, Reservation.ReservationStatus status);

    // 예약 가능 여부 확인
    boolean canReserve(Video video, int quantity);
}