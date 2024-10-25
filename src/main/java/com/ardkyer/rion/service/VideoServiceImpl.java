package com.ardkyer.rion.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.ardkyer.rion.entity.*;
import com.ardkyer.rion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {

    @Autowired
    private VideoRepository videoRepository;
    private final CommentRepository commentRepository;
    private final HashtagRepository hashtagRepository;
    private final AmazonS3 amazonS3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Autowired
    public VideoServiceImpl(VideoRepository videoRepository,
                            CommentRepository commentRepository,
                            HashtagRepository hashtagRepository,
                            AmazonS3 amazonS3Client) {
        this.videoRepository = videoRepository;
        this.commentRepository = commentRepository;
        this.hashtagRepository = hashtagRepository;
        this.amazonS3Client = amazonS3Client;
    }

    @Override
    @Transactional
    public Video uploadVideo(Video video, MultipartFile file, Set<String> hashtagNames) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        amazonS3Client.putObject(bucketName, fileName, file.getInputStream(), metadata);

        video.setImageUrl(fileName);

        Set<Hashtag> hashtags = convertNamesToHashtags(hashtagNames);
        video.setHashtags(hashtags);

        return videoRepository.save(video);
    }

    private Set<Hashtag> convertNamesToHashtags(Set<String> hashtagNames) {
        return hashtagNames.stream()
                .map(name -> hashtagRepository.findByName(name)
                        .orElseGet(() -> {
                            Hashtag newHashtag = new Hashtag();
                            newHashtag.setName(name);
                            return hashtagRepository.save(newHashtag);
                        }))
                .collect(Collectors.toSet());
    }

    @Override
    public S3Object getVideoFile(String fileName) {
        return amazonS3Client.getObject(bucketName, fileName);
    }

    @Override
    public Optional<Video> getVideoById(Long id) {
        return videoRepository.findById(id);
    }

    @Override
    public List<Video> getVideosByUser(User user) {
        return videoRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public Video updateVideo(Video video) {
        return videoRepository.save(video);
    }

    @Override
    @Transactional
    public void deleteVideo(Long id) {
        Optional<Video> videoOptional = videoRepository.findById(id);
        if (videoOptional.isPresent()) {
            Video video = videoOptional.get();
            amazonS3Client.deleteObject(bucketName, video.getImageUrl());
            videoRepository.deleteById(id);
        } else {
            throw new RuntimeException("Video not found with id: " + id);
        }
    }

    @Override
    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    @Override
    public List<Video> getAllVideosWithComments() {
        List<Video> videos = videoRepository.findAll();
        videos.forEach(video -> video.getComments().size()); // Fetch comments
        return videos;
    }

    @Override
    public List<Video> getAllVideosWithSortedComments() {
        List<Video> videos = videoRepository.findAll();
        videos.forEach(video -> {
            Page<Comment> commentsPage = commentRepository.findByVideoOrderByCreatedAtDesc(video, PageRequest.of(0, 5));
            video.setComments(new ArrayList<>(commentsPage.getContent())); // Page를 List로 변환
        });
        return videos;
    }

    @Override
    public void saveHashtagsFromDescription(String description) {
        if (description != null && !description.trim().isEmpty()) {
            Set<String> hashtags = Arrays.stream(description.split(" "))
                    .filter(tag -> tag.startsWith("#"))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            convertNamesToHashtags(hashtags);
        }
    }

    @Override
    public List<Video> getReservedVideosByUser(User user) {
        return videoRepository.findByReservationsUser(user);
    }

    @Override
    public boolean isAvailableForReservation(Long videoId, int quantity) {
        Optional<Video> videoOpt = videoRepository.findById(videoId);
        if (videoOpt.isPresent()) {
            Video video = videoOpt.get();
            return video.getAvailableQuantity() >= quantity &&
                    video.getReservationStatus() == Video.ReservationStatus.AVAILABLE;
        }
        return false;
    }

    @Override
    public Video updateAvailableQuantity(Long videoId, int quantity) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        video.setAvailableQuantity(video.getAvailableQuantity() - quantity);
        return videoRepository.save(video);
    }

    @Override
    public List<Video> searchVideos(String query) {
        return videoRepository.findByTitleContainingOrDescriptionContaining(query, query);
    }

    @Override
    public List<Video> searchVideosByHashtags(Set<String> hashtags) {
        return videoRepository.findByHashtagsNameIn(hashtags);
    }

    @Override
    public List<Video> getRandomRecentVideos(int count) {
        List<Video> recentVideos = videoRepository.findRecentVideos(PageRequest.of(0, count));
        Collections.shuffle(recentVideos);
        return recentVideos.stream().limit(count).collect(Collectors.toList());
    }

    @Override
    public List<Video> getRecentVideosByUser(User user, int limit) {
        return videoRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, limit));
    }
}