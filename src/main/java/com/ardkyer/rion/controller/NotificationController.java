package com.ardkyer.rion.controller;

import com.ardkyer.rion.entity.Notification;
import com.ardkyer.rion.repository.NotificationRepository;
import com.ardkyer.rion.security.PrincipalDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    public String getNotifications(Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        // 모든 알림을 가져옴 (읽은 것 포함)
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(principalDetails.getUser());
        model.addAttribute("notifications", notifications);
        return "notifications";
    }



    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);


    @GetMapping("/read/{notificationId}")
    public String markAsReadAndRedirect(@PathVariable Long notificationId, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        System.out.println("markAsReadAndRedirect 메서드 시작");

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid notification ID"));

        System.out.println("알림 ID: " + notificationId + "를 찾았습니다.");

        notification.setRead(true);
        notificationRepository.save(notification);

        System.out.println("알림 ID: " + notificationId + "가 읽음 처리되었습니다.");

        Long videoId = notification.getVideoId();
        if (videoId == null) {
            throw new IllegalArgumentException("Video ID is null for this notification");
        }

        System.out.println("비디오 ID: " + videoId + "로 리다이렉트 중입니다.");
        return "redirect:/videos/detail/" + videoId;
    }

    @PostMapping("/delete-read")
    public String deleteReadNotifications(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        List<Notification> readNotifications = notificationRepository.findByUserAndIsReadTrue(principalDetails.getUser());
        notificationRepository.deleteAll(readNotifications);
        return "redirect:/notifications";
    }
}

