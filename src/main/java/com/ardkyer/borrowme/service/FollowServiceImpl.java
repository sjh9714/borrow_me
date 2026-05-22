package com.ardkyer.borrowme.service;

import com.ardkyer.borrowme.entity.*;
import com.ardkyer.borrowme.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl implements FollowService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Autowired
    public FollowServiceImpl(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Follow followUser(User follower, User followed) {
        if (isFollowing(follower, followed)) {
            throw new IllegalStateException("Already following this user");
        }
        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowed(followed);
        return followRepository.save(follow);
    }

    @Override
    @Transactional
    public void unfollowUser(User follower, User followed) {
        followRepository.findByFollowerAndFollowed(follower, followed)
                .ifPresent(follow -> followRepository.delete(follow));
    }

    @Override
    public boolean isFollowing(User follower, User followed) {
        return followRepository.existsByFollowerAndFollowed(follower, followed);
    }

    @Override
    public Set<Long> getFollowedUserIds(User follower, List<User> candidates) {
        return followRepository.findByFollowerAndFollowedIn(follower, candidates).stream()
                .map(follow -> follow.getFollowed().getId())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Long> getFollowedIds(User follower, List<User> candidates) {
        return new HashSet<>(followRepository.findFollowedIdsByFollowerAndFollowedIn(follower, candidates));
    }

    @Override
    public List<User> getFollowers(User user) {
        return followRepository.findByFollowed(user).stream()
                .map(Follow::getFollower)
                .toList();
    }

    @Override
    public List<User> getFollowing(User user) {
        List<Follow> followings = followRepository.findByFollower(user);
        return followings.stream()
                .map(Follow::getFollowed)
                .collect(Collectors.toList());
    }

    @Override
    public long getFollowerCount(User user) {
        return followRepository.countByFollowed(user);
    }

    @Override
    public long getFollowingCount(User user) {
        return followRepository.countByFollower(user);
    }

    @Override
    @Transactional
    public boolean toggleFollow(User follower, User followed) {
        if (isFollowing(follower, followed)) {
            unfollowUser(follower, followed);
            return false;
        } else {
            followUser(follower, followed);
            return true;
        }
    }
}
