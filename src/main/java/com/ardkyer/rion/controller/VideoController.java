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
        headers.setContentType(MediaType.IMAGE_JPEG); // 이미지 타입으로 변경
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
        // 이미지 파일 타입 검증
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

    // 예약 관련 엔드포인트 추가
    @PostMapping("/{id}/reserve")
    @ResponseBody
    public ResponseEntity<?> reserveItem(@PathVariable Long id,
                                         @RequestParam Integer quantity,
                                         Authentication authentication) {
        User currentUser = userService.findByUsername(authentication.getName());
        Video video = videoService.getVideoById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        try {
            Reservation reservation = reservationService.reserve(video, currentUser, quantity);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("reservationId", reservation.getId());
            response.put("remainingQuantity", video.getAvailableQuantity());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelReservation(@PathVariable Long id, Authentication authentication) {
        try {
            reservationService.cancelReservation(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
                Optional<Reservation> userReservation = reservationService.getReservationByVideoAndUser(video, currentUser);
                model.addAttribute("userReservation", userReservation.orElse(null));
            }

            model.addAttribute("video", video);
            model.addAttribute("isFollowing", isFollowing);
            model.addAttribute("currentUser", getCurrentUser(authentication));
            return "detailPage";
        } else {
            return "redirect:/videos";
        }
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