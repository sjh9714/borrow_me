package com.ardkyer.rion.service;

import com.amazonaws.services.s3.model.S3Object;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ardkyer.rion.entity.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public interface VideoService {
    // 기본 CRUD 작업
    Video uploadVideo(Video video, MultipartFile file, Set<String> hashtagNames) throws IOException;
    Optional<Video> getVideoById(Long id);
    Video updateVideo(Video video);
    void deleteVideo(Long id);

    // 파일 처리
    S3Object getVideoFile(String fileName);

    // 조회 메서드
    List<Video> getAllVideos();
    List<Video> getVideosByUser(User user);
    List<Video> getRecentVideosByUser(User user, int limit);
    List<Video> getRandomRecentVideos(int count);

    // 예약 관련 메서드
    List<Video> getReservedVideosByUser(User user);
    boolean isAvailableForReservation(Long videoId, int quantity);
    Video updateAvailableQuantity(Long videoId, int quantity);

    // 검색 관련 메서드
    List<Video> searchVideos(String query);
    List<Video> searchVideosByHashtags(Set<String> hashtags);
    void saveHashtagsFromDescription(String description);

    // 댓글 관련
    List<Video> getAllVideosWithComments();
    List<Video> getAllVideosWithSortedComments();
}