//UserController.java
package com.ardkyer.rion.controller;

import com.ardkyer.rion.dto.EmailVerificationDto;
import com.ardkyer.rion.dto.UserRegistrationDto;
import com.ardkyer.rion.entity.*;
import com.ardkyer.rion.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@Tag(name = "User", description = "User management API")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final VideoService videoService;
    private final CommentService commentService;
    private final FollowService followService;

    @PostMapping("/api/users/register")
    @ResponseBody
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationDto registrationDto) {
        log.info("Received registration request - username: {}, email: {}",
                registrationDto.getUsername(), registrationDto.getEmail());

        try {
            // 이메일 인증 확인
            boolean isVerified = userService.isEmailVerified(registrationDto.getEmail());
            log.info("Email verification status for {}: {}", registrationDto.getEmail(), isVerified);

            if (!isVerified) {
                return ResponseEntity.badRequest().body("이메일 인증이 필요합니다.");
            }

            User registeredUser = userService.registerNewUser(registrationDto);
            log.info("User registered successfully - id: {}", registeredUser.getId());
            return ResponseEntity.ok(registeredUser);
        } catch (Exception e) {
            log.error("Registration failed: ", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/users/{id}")
    @ResponseBody
    @Operation(summary = "Get a user by ID", description = "Retrieves a user's information by their ID")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<User> getUserById(
            @Parameter(description = "ID of the user to retrieve", required = true) @PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> new ResponseEntity<>(user, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    @Operation(summary = "Update a user", description = "Updates an existing user's information")
    @ApiResponse(responseCode = "200", description = "Successfully updated user", content = @Content(schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<User> updateUser(
            @Parameter(description = "ID of the user to update", required = true) @PathVariable Long id,
            @RequestBody User user) {
        if (!userService.getUserById(id).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        user.setId(id);
        User updatedUser = userService.updateUser(user);
        return new ResponseEntity<>(updatedUser, HttpStatus.OK);
    }

    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    @Operation(summary = "Delete a user", description = "Deletes an existing user")
    @ApiResponse(responseCode = "204", description = "Successfully deleted user")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "ID of the user to delete", required = true) @PathVariable Long id) {
        if (!userService.getUserById(id).isPresent()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        userService.deleteUser(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/profile")
    @Operation(summary = "Show user profile", description = "Displays the profile page for the authenticated user")
    public String showProfile(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        List<Video> userVideos = videoService.getVideosByUser(currentUser);
        List<Video> likedVideos = videoService.getLikedVideosByUser(currentUser);
        List<Comment> userComments = commentService.getCommentsByUser(currentUser);

        model.addAttribute("user", currentUser);
        model.addAttribute("followerCount", followService.getFollowerCount(currentUser));
        model.addAttribute("followingCount", followService.getFollowingCount(currentUser));
        model.addAttribute("followers", followService.getFollowers(currentUser));
        model.addAttribute("following", followService.getFollowing(currentUser));
        model.addAttribute("userVideos", userVideos);
        model.addAttribute("likedVideos", likedVideos);
        model.addAttribute("userComments", userComments);

        return "profile";
    }

    @PostMapping("/profile/delete-video/{id}")
    @Operation(summary = "Delete a video", description = "Deletes a video owned by the authenticated user")
    @ApiResponse(responseCode = "302", description = "Redirects to profile page after deletion")
    public String deleteVideo(
            @Parameter(description = "ID of the video to delete", required = true) @PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByUsername(auth.getName());

        Optional<Video> videoOptional = videoService.getVideoById(id);
        if (videoOptional.isPresent() && videoOptional.get().getUser().getId().equals(currentUser.getId())) {
            videoService.deleteVideo(id);
        }

        return "redirect:/profile";
    }

    @GetMapping("/user/{username}/videos")
    public String showUserVideos(@PathVariable String username, Model model) {
        User user = userService.findByUsername(username);
        if (user == null) {
            return "error"; // 사용자를 찾을 수 없을 때 에러 페이지로 리다이렉트
        }
        List<Video> userVideos = videoService.getVideosByUser(user);
        model.addAttribute("user", user);
        model.addAttribute("userVideos", userVideos);
        return "user-videos";
    }

    @PostMapping("/api/auth/send-verification")
    @ResponseBody
    @Operation(summary = "Send verification email", description = "Sends verification code to user's email")
    @ApiResponse(responseCode = "200", description = "Successfully sent verification code")
    @ApiResponse(responseCode = "400", description = "Invalid email address")
    public ResponseEntity<String> sendVerificationEmail(@RequestParam String email) {
        try {
            userService.sendVerificationEmail(email);
            return ResponseEntity.ok("인증 코드가 발송되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("인증 코드 발송 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/api/auth/verify-email")
    @ResponseBody
    @Operation(summary = "Verify email", description = "Verifies user's email with provided code")
    @ApiResponse(responseCode = "200", description = "Successfully verified email")
    @ApiResponse(responseCode = "400", description = "Invalid verification code")
    public ResponseEntity<String> verifyEmail(@RequestBody EmailVerificationDto dto) {
        try {
            boolean verified = userService.verifyEmail(dto.getEmail(), dto.getVerificationCode());
            if (verified) {
                return ResponseEntity.ok("이메일이 인증되었습니다.");
            } else {
                return ResponseEntity.badRequest().body("잘못된 인증 코드입니다.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("이메일 인증 중 오류가 발생했습니다.");
        }
    }

}
