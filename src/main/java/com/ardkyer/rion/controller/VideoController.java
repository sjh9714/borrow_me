package com.ardkyer.rion.controller;

import com.amazonaws.services.s3.model.S3Object;
import com.ardkyer.rion.entity.Comment;
import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Reservation;
import com.ardkyer.rion.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.io.IOException;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/videos")
@Tag(name = "Video", description = "Video management API")
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private FollowService followService;

    // DTO classes for request handling
    @Getter @Setter
    public static class ReservationRequest {
        private Integer quantity;
    }

    @Getter @Setter
    public static class StatusUpdateRequest {
        private String status;
    }

    @GetMapping
    @Operation(summary = "List all items", description = "Retrieves a list of all items with comments")
    public String listVideos(Model model, Authentication authentication) {
        List<Video> videos = videoService.getAllVideos();
        prepareVideosForDisplay(videos, authentication);
        model.addAttribute("videos", videos);
        model.addAttribute("currentUser", getCurrentUser(authentication));
        return "videos";
    }

    @GetMapping("/{id}")
    @Operation(summary = "View item details", description = "Retrieves a specific item for viewing")
    public String watchVideo(@Parameter(description = "ID of the item") @PathVariable Long id, Model model, Authentication authentication) {
        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isPresent()) {
            Video video = videoOptional.get();
            prepareVideoForDisplay(video, authentication);
            model.addAttribute("video", video);
            return "watchVideo";
        } else {
            return "redirect:/videos";
        }
    }

    @GetMapping("/file/{fileName:.+}")
    @Operation(summary = "Serve image file", description = "Serves an image file")
    public ResponseEntity<InputStreamResource> serveFile(@Parameter(description = "Name of the file to serve") @PathVariable String fileName) {
        S3Object s3Object = videoService.getVideoFile(fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setContentLength(s3Object.getObjectMetadata().getContentLength());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(s3Object.getObjectContent()));
    }

    @GetMapping("/upload")
    @Operation(summary = "Show upload form", description = "Displays the item upload form")
    public String showUploadForm() {
        return "uploadForm";
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload an item", description = "Uploads a new item with image")
    public String handleFileUpload(@RequestParam("title") String title,
                                   @RequestParam("description") String description,
                                   @RequestParam("image") MultipartFile file,
                                   @RequestParam("totalQuantity") Integer totalQuantity,
                                   @RequestParam(value = "hashtags", required = false) String hashtags,
                                   Authentication authentication) throws IOException {
        if (!file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed!");
        }

        User currentUser = userService.findByUsername(authentication.getName());

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setUser(currentUser);
        video.setTotalQuantity(totalQuantity);
        video.setAvailableQuantity(totalQuantity);
        video.setReservationStatus(Video.ReservationStatus.AVAILABLE);

        Set<String> hashtagSet = extractHashtags(description, hashtags);
        videoService.uploadVideo(video, file, hashtagSet);

        return "redirect:/videos";
    }

    @PostMapping("/{id}/reserve")
    @ResponseBody
    public ResponseEntity<?> reserveItem(@PathVariable Long id,
                                         @RequestBody ReservationRequest request,
                                         Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            // 본인 상품 예약 방지
            if (video.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "자신의 상품은 예약할 수 없습니다."
                ));
            }

            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid quantity"
                ));
            }

            if (request.getQuantity() > video.getAvailableQuantity()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "재고가 부족합니다."
                ));
            }

            Reservation reservation = reservationService.reserve(video, currentUser, request.getQuantity());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reservationId", reservation.getId(),
                    "remainingQuantity", video.getAvailableQuantity()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, Authentication authentication) {
        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isPresent()) {
            Video video = videoOptional.get();

            // 작성자 확인
            User currentUser = userService.findByUsername(authentication.getName());
            if (!video.getUser().getId().equals(currentUser.getId())) {
                return "redirect:/videos";
            }

            model.addAttribute("video", video);
            return "editForm";  // editForm.html 템플릿 필요
        }
        return "redirect:/videos";
    }

    @PostMapping("/edit/{id}")
    public String updateVideo(@PathVariable Long id,
                              @RequestParam("title") String title,
                              @RequestParam("description") String description,
                              @RequestParam(value = "image", required = false) MultipartFile file,
                              @RequestParam("totalQuantity") Integer totalQuantity,
                              Authentication authentication) throws IOException {
        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isPresent()) {
            Video video = videoOptional.get();

            // 작성자 확인
            User currentUser = userService.findByUsername(authentication.getName());
            if (!video.getUser().getId().equals(currentUser.getId())) {
                return "redirect:/videos";
            }

            // 기본 정보 업데이트
            video.setTitle(title);
            video.setDescription(description);
            video.setTotalQuantity(totalQuantity);

            // 이미지가 제공된 경우에만 업데이트
            if (file != null && !file.isEmpty()) {
                if (!file.getContentType().startsWith("image/")) {
                    throw new IllegalArgumentException("Only image files are allowed!");
                }
                videoService.updateVideoWithImage(video, file);
            } else {
                videoService.updateVideo(video);
            }
        }
        return "redirect:/videos/detail/" + id;
    }

    @PutMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody StatusUpdateRequest request,
                                          Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            if (!video.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "권한이 없습니다."
                ));
            }

            try {
                Video.ReservationStatus newStatus = Video.ReservationStatus.valueOf(request.getStatus().toUpperCase());
                video.setReservationStatus(newStatus);
                video = videoService.updateVideo(video);  // 업데이트된 비디오 객체 받기

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "status", video.getReservationStatus()
                ));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid status value"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteVideo(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            if (!video.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "권한이 없습니다."
                ));
            }

            videoService.deleteVideo(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelReservation(@PathVariable Long id, Authentication authentication) {
        try {
            reservationService.cancelReservation(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model, Authentication authentication) {
        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isPresent()) {
            Video video = videoOptional.get();
            prepareVideoForDisplay(video, authentication);

            boolean isFollowing = false;
            if (authentication != null) {
                User currentUser = userService.findByUsername(authentication.getName());
                isFollowing = followService.isFollowing(currentUser, video.getUser());

                // 현재 사용자의 예약 정보 추가
                List<Reservation> userReservations = reservationService.getReservationByVideoAndUser(video, currentUser);
                model.addAttribute("userReservations", userReservations);
                model.addAttribute("hasReservation", !userReservations.isEmpty());
            }

            model.addAttribute("video", video);
            model.addAttribute("isFollowing", isFollowing);
            model.addAttribute("currentUser", getCurrentUser(authentication));
            return "detailPage";
        }
        return "redirect:/videos";
    }

    private void prepareVideosForDisplay(List<Video> videos, Authentication authentication) {
        User currentUser = getCurrentUser(authentication).orElse(null);
        for (Video video : videos) {
            if (currentUser != null) {
                video.setFollowedByCurrentUser(followService.isFollowing(currentUser, video.getUser()));
            }
        }
    }

    private void prepareVideoForDisplay(Video video, Authentication authentication) {
        User currentUser = getCurrentUser(authentication).orElse(null);
        if (currentUser != null) {
            video.setFollowedByCurrentUser(followService.isFollowing(currentUser, video.getUser()));
        }
    }

    private Optional<User> getCurrentUser(Authentication authentication) {
        if (authentication != null) {
            return userService.getUserByUsername(authentication.getName());
        }
        return Optional.empty();
    }

    private Set<String> extractHashtags(String description, String additionalHashtags) {
        Set<String> hashtagSet = Arrays.stream(description.split(" "))
                .map(String::trim)
                .filter(tag -> tag.startsWith("#"))
                .collect(Collectors.toSet());

        if (additionalHashtags != null && !additionalHashtags.trim().isEmpty()) {
            hashtagSet.addAll(Arrays.stream(additionalHashtags.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet()));
        }

        return hashtagSet;
    }
}