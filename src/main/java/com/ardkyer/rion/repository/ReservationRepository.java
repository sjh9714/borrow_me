package com.ardkyer.rion.repository;

import com.ardkyer.rion.entity.Reservation;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUser(User user);
    List<Reservation> findByVideo(Video video);
    Optional<Reservation> findByVideoAndUser(Video video, User user);
    List<Reservation> findByVideoAndStatus(Video video, Reservation.ReservationStatus status);
}