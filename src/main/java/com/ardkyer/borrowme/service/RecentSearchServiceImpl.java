package com.ardkyer.borrowme.service;

import com.ardkyer.borrowme.entity.RecentSearch;
import com.ardkyer.borrowme.entity.User;
import com.ardkyer.borrowme.repository.RecentSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecentSearchServiceImpl implements RecentSearchService {

    @Autowired
    private RecentSearchRepository recentSearchRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RecentSearch> getRecentSearches(User user) {
        return recentSearchRepository.findByUserOrderBySearchTimeDesc(user);
    }

    @Override
    @Transactional
    public void addOrUpdateRecentSearch(User user, String keyword) {
        recentSearchRepository.upsertByUserIdAndKeyword(
                user.getId(),
                keyword,
                LocalDateTime.now()
        );
    }

    @Override
    @Transactional
    public void deleteRecentSearch(User user, String keyword) {
        recentSearchRepository.deleteByUserAndKeyword(user, keyword);
    }

    @Override
    @Transactional  // 트랜잭션 적용
    public void deleteAllRecentSearches(User user) {
        // user에 대한 모든 최근 검색 기록 삭제하는 로직 구현
        recentSearchRepository.deleteAllByUser(user);
    }
}
