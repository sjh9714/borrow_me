package com.ardkyer.rion.repository;

import com.ardkyer.rion.entity.Notification;
import com.ardkyer.rion.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user); // 읽지 않은 알림만 가져오는 메서드
    int countByUserAndIsReadFalse(User user); // 읽지 않은 알림 개수 카운트
    List<Notification> findByUserAndIsReadTrue(User user); // 읽은 알림 가져오기
}
