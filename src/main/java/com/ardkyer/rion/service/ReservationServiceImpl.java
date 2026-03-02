package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Reservation;
import com.ardkyer.rion.repository.ReservationRepository;
import com.ardkyer.rion.repository.VideoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Reservation reserve(Video video, User user, int quantity) {
        // L1 캐시에서 기존 Video 엔티티를 제거하여 SELECT FOR UPDATE가 실제 DB 조회하도록 보장
        entityManager.detach(video);

        // Pessimistic Lock으로 Video를 재조회하여 동시성 보장
        Video lockedVideo = videoRepository.findByIdForUpdate(video.getId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if (lockedVideo.getAvailableQuantity() < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }

        // 새 예약 생성
        Reservation reservation = new Reservation();
        reservation.setVideo(lockedVideo);
        reservation.setUser(user);
        reservation.setQuantity(quantity);
        reservation.setStatus(Reservation.ReservationStatus.PENDING);

        // 재고 수량 감소
        lockedVideo.setAvailableQuantity(lockedVideo.getAvailableQuantity() - quantity);
        if (lockedVideo.getAvailableQuantity() == 0) {
            lockedVideo.setReservationStatus(Video.ReservationStatus.OUT_OF_STOCK);
        } else {
            lockedVideo.setReservationStatus(Video.ReservationStatus.RESERVED);
        }

        videoRepository.save(lockedVideo);
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
    @Transactional(readOnly = true)
    public List<Reservation> getUserReservations(User user) {
        return reservationRepository.findByUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> getVideoReservations(Video video) {
        return reservationRepository.findByVideo(video);
    }

    @Override
    public List<Reservation> getReservationByVideoAndUser(Video video, User user) {
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
    @Transactional(readOnly = true)
    public boolean canReserve(Video video, int quantity) {
        return video.getAvailableQuantity() >= quantity &&
                video.getReservationStatus() != Video.ReservationStatus.OUT_OF_STOCK;
    }
}