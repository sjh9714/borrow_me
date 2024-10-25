package com.ardkyer.rion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String content) {
        log.info("Attempting to send email to: {}", to);  // 로그 추가
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("REDACTED_EMAIL");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            log.info("Preparing to send email...");  // 로그 추가
            mailSender.send(message);
            log.info("Email sent successfully");  // 로그 추가
        } catch (Exception e) {
            log.error("Failed to send email: ", e);  // 스택트레이스 출력
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }
}