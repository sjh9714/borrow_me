package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Reservation;
import com.ardkyer.rion.repository.ReservationRepository;
import com.ardkyer.rion.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReservationServiceImpl implements ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Override
    @Transactional
    public Reservation reserve(Video video, User user, int quantity) {
        // 예약 가능 여부 확인
        if (!canReserve(video, quantity)) {
            throw new IllegalStateException("예약할 수 없는 상품입니다.");
        }

        // 이미 해당 사용자가 이 상품을 예약했는지 확인
        Optional<Reservation> existingReservation = getReservationByVideoAndUser(video, user);
        if (existingReservation.isPresent()) {
            throw new IllegalStateException("이미 예약한 상품입니다.");
        }

        // 예약 생성
        Reservation reservation = new Reservation();
        reservation.setVideo(video);
        reservation.setUser(user);
        reservation.setQuantity(quantity);
        reservation.setStatus(Reservation.ReservationStatus.PENDING);

        // 재고 수량 감소
        video.setAvailableQuantity(video.getAvailableQuantity() - quantity);
        if (video.getAvailableQuantity() == 0) {
            video.setReservationStatus(Video.ReservationStatus.OUT_OF_STOCK);
        } else {
            video.setReservationStatus(Video.ReservationStatus.RESERVED);
        }

        videoRepository.save(video);
        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // 예약 취소 시 재고 수량 증가
        Video video = reservation.getVideo();
        video.setAvailableQuantity(video.getAvailableQuantity() + reservation.getQuantity());

        // 재고 상태 업데이트
        if (video.getAvailableQuantity() > 0) {
            video.setReservationStatus(Video.ReservationStatus.AVAILABLE);
        }

        videoRepository.save(video);
        reservation.setStatus(Reservation.ReservationStatus.CANCELED);
        reservationRepository.save(reservation);
    }

    @Override
    public List<Reservation> getUserReservations(User user) {
        return reservationRepository.findByUser(user);
    }

    @Override
    public List<Reservation> getVideoReservations(Video video) {
        return reservationRepository.findByVideo(video);
    }

    @Override
    public Optional<Reservation> getReservationByVideoAndUser(Video video, User user) {
        return reservationRepository.findByVideoAndUser(video, user);
    }

    @Override
    @Transactional
    public Reservation updateReservationStatus(Long reservationId, Reservation.ReservationStatus status) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        reservation.setStatus(status);
        return reservationRepository.save(reservation);
    }

    @Override
    public boolean canReserve(Video video, int quantity) {
        return video.getAvailableQuantity() >= quantity &&
                video.getReservationStatus() != Video.ReservationStatus.OUT_OF_STOCK;
    }
}