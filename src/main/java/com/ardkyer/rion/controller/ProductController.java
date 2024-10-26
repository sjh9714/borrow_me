package com.ardkyer.rion.controller;

import com.amazonaws.services.s3.model.S3Object;
import com.ardkyer.rion.entity.Comment;
import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Reservation;
import com.ardkyer.rion.entity.Hashtag;
import com.ardkyer.rion.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.io.IOException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product management API")
@RequiredArgsConstructor
public class ProductController {

    private final VideoService videoService;
    private final UserService userService;
    private final ReservationService reservationService;
    private final CommentService commentService;
    private final FollowService followService;

    // Request/Response DTOs
    @Getter @Setter
    public static class ReservationRequest {
        private Integer quantity;
    }

    @Getter @Setter
    public static class ProductResponse {
        private Long id;
        private String title;
        private String description;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private String status;
        private UserInfo user;
        private boolean isFollowedByCurrentUser;
        private List<String> hashtags;
        private String imageUrl;

        @Getter @Setter
        public static class UserInfo {
            private Long id;
            private String username;
        }
    }

    @GetMapping
    @Operation(summary = "List all products", description = "Retrieves a list of all available products")
    public ResponseEntity<List<ProductResponse>> getProducts(Authentication authentication) {
        List<Video> videos = videoService.getAllVideos();
        List<ProductResponse> response = videos.stream()
                .map(video -> convertToProductResponse(video, authentication))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product details", description = "Retrieves details of a specific product")
    public ResponseEntity<ProductResponse> getProduct(
            @Parameter(description = "ID of the product") @PathVariable Long id,
            Authentication authentication) {
        return videoService.getVideoById(id)
                .map(video -> ResponseEntity.ok(convertToProductResponse(video, authentication)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a product", description = "Creates a new product with image")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("image") MultipartFile file,
            @RequestParam("totalQuantity") Integer totalQuantity,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            Authentication authentication) throws IOException {

        if (!file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().build();
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
        video = videoService.uploadVideo(video, file, hashtagSet);

        return ResponseEntity.ok(convertToProductResponse(video, authentication));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product", description = "Updates an existing product")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "image", required = false) MultipartFile file,
            @RequestParam("totalQuantity") Integer totalQuantity,
            Authentication authentication) throws IOException {

        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = videoOptional.get();
        User currentUser = userService.findByUsername(authentication.getName());

        if (!video.getUser().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).build();
        }

        video.setTitle(title);
        video.setDescription(description);
        video.setTotalQuantity(totalQuantity);

        if (file != null && !file.isEmpty()) {
            if (!file.getContentType().startsWith("image/")) {
                return ResponseEntity.badRequest().build();
            }
            video = videoService.updateVideoWithImage(video, file);
        } else {
            video = videoService.updateVideo(video);
        }

        return ResponseEntity.ok(convertToProductResponse(video, authentication));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product", description = "Deletes an existing product")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (!video.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).build();
            }

            videoService.deleteVideo(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/reserve")
    @Operation(summary = "Reserve a product", description = "Creates a reservation for a product")
    public ResponseEntity<?> createReservation(
            @PathVariable Long id,
            @RequestBody ReservationRequest request,
            Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Video video = videoService.getVideoById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (video.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Cannot reserve your own product"));
            }

            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid quantity"));
            }

            if (request.getQuantity() > video.getAvailableQuantity()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Insufficient stock"));
            }

            Reservation reservation = reservationService.reserve(video, currentUser, request.getQuantity());

            return ResponseEntity.ok(Map.of(
                    "reservationId", reservation.getId(),
                    "remainingQuantity", video.getAvailableQuantity()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/images/{fileName:.+}")
    @Operation(summary = "Get product image", description = "Retrieves a product image")
    public ResponseEntity<InputStreamResource> getProductImage(
            @Parameter(description = "Name of the image file")
            @PathVariable String fileName) {
        S3Object s3Object = videoService.getVideoFile(fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setContentLength(s3Object.getObjectMetadata().getContentLength());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(s3Object.getObjectContent()));
    }

    private ProductResponse convertToProductResponse(Video video, Authentication authentication) {
        ProductResponse response = new ProductResponse();
        response.setId(video.getId());
        response.setTitle(video.getTitle());
        response.setDescription(video.getDescription());
        response.setTotalQuantity(video.getTotalQuantity());
        response.setAvailableQuantity(video.getAvailableQuantity());
        response.setStatus(video.getReservationStatus().name());

        // Convert Hashtag entities to strings
        List<String> hashtagStrings = video.getHashtags().stream()
                .map(Hashtag::getName)
                .collect(Collectors.toList());
        response.setHashtags(hashtagStrings);

        // Use the correct method name for getting image file name
        response.setImageUrl("/api/products/images/" + video.getImageUrl());

        ProductResponse.UserInfo userInfo = new ProductResponse.UserInfo();
        userInfo.setId(video.getUser().getId());
        userInfo.setUsername(video.getUser().getUsername());
        response.setUser(userInfo);

        if (authentication != null) {
            User currentUser = userService.findByUsername(authentication.getName());
            response.setFollowedByCurrentUser(followService.isFollowing(currentUser, video.getUser()));
        }

        return response;
    }

    private Set<String> extractHashtags(String description, String additionalHashtags) {
        Set<String> hashtagSet = Arrays.stream(description.split(" "))
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