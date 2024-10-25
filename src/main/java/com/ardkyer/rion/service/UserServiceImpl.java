package com.ardkyer.rion.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.ardkyer.rion.dto.UserRegistrationDto;
import com.ardkyer.rion.entity.EmailVerification;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.repository.EmailVerificationRepository;
import com.ardkyer.rion.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AmazonS3 amazonS3Client;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("ardkyerspring2")
    private String bucketName;

    @Autowired
    public UserServiceImpl(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AmazonS3 amazonS3Client) {
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.amazonS3Client = amazonS3Client;
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    @Override
    @Transactional
    public void sendVerificationEmail(String email) {
        log.info("Starting email verification process for: {}", email);
        try {
            if (!email.endsWith("@catholic.ac.kr")) {
                log.warn("Invalid email domain: {}", email);
                throw new IllegalArgumentException("가톨릭대학교 메일만 사용 가능합니다.");
            }

            // 이메일 중복 체크 추가
            if (userRepository.existsByEmail(email)) {
                log.warn("Email already exists: {}", email);
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }

            String verificationCode = generateVerificationCode();
            log.info("Generated verification code: {}", verificationCode);

            // 이메일 인증 정보 저장
            EmailVerification verification = emailVerificationRepository
                    .findByEmail(email)
                    .orElse(new EmailVerification());

            verification.setEmail(email);
            verification.setVerificationCode(verificationCode);
            verification.setExpiryDate(LocalDateTime.now().plusMinutes(30));
            verification.setVerified(false);

            emailVerificationRepository.save(verification);

            emailService.sendEmail(email, "이메일 인증 코드",
                    String.format("인증 코드: %s\n\n이 코드는 30분 동안 유효합니다.", verificationCode));
            log.info("Email sent successfully");

        } catch (Exception e) {
            log.error("Error in sendVerificationEmail: ", e);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean verifyEmail(String email, String code) {
        EmailVerification verification = emailVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("인증 정보를 찾을 수 없습니다."));

        if (verification.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("인증 코드가 만료되었습니다.");
        }

        if (verification.getVerificationCode().equals(code)) {
            verification.setVerified(true);
            emailVerificationRepository.save(verification);
            return true;
        }
        return false;
    }

    @Override
    public boolean isEmailVerified(String email) {
        return emailVerificationRepository.findByEmail(email)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    @Override
    @Transactional
    public User registerNewUser(UserRegistrationDto registrationDto) {
        log.info("Starting registration for user: {}", registrationDto.getUsername());

        // 이메일 인증 확인
        EmailVerification verification = emailVerificationRepository.findByEmail(registrationDto.getEmail())
                .orElseThrow(() -> new RuntimeException("이메일 인증이 필요합니다."));

        if (!verification.isVerified()) {
            throw new RuntimeException("이메일 인증이 필요합니다.");
        }

        // 중복 체크
        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 사용자 생성
        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registrationDto.getPassword()));
        user.setEmailVerified(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Collections.singleton("ROLE_USER"));

        log.info("Saving new user to database");
        User savedUser = userRepository.save(user);
        log.info("User saved successfully with id: {}", savedUser.getId());

        return savedUser;
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public List<User> getTopUsersByFollowerCount(int limit) {
        return userRepository.findTopUsersByFollowerCount(limit);
    }

    @Override
    @Transactional
    public String updateUserAvatar(String username, MultipartFile file) throws IOException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String fileName = "avatars/" + UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucketName, fileName, file.getInputStream(), metadata);

        String avatarUrl = amazonS3Client.getUrl(bucketName, fileName).toString();
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }

    @Override
    @Transactional
    public User registerUser(User user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        return userRepository.save(user);
    }
}