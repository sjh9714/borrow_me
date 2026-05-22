package com.ardkyer.borrowme.service;

import com.ardkyer.borrowme.entity.*;
import com.ardkyer.borrowme.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FollowService {
    Follow followUser(User follower, User followed);
    void unfollowUser(User follower, User followed);
    boolean isFollowing(User follower, User followed);
    Set<Long> getFollowedUserIds(User follower, List<User> candidates);
    Set<Long> getFollowedIds(User follower, List<User> candidates);
    List<User> getFollowers(User user);
    List<User> getFollowing(User user);
    long getFollowerCount(User user);
    long getFollowingCount(User user);
    boolean toggleFollow(User follower, User followed);
}
