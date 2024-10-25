package com.ardkyer.rion.service;

import com.ardkyer.rion.entity.ItemStatus;
import com.ardkyer.rion.entity.ItemUnit;
import com.ardkyer.rion.entity.User;
import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.repository.ItemUnitRepository;
import com.ardkyer.rion.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemUnitService {

    @Autowired
    private ItemUnitRepository itemUnitRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public void updateUnitStatus(Long unitId, ItemStatus status, Authentication authentication) {
        ItemUnit unit = itemUnitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found"));

        User currentUser = userService.findByUsername(authentication.getName());

        // 작성자 권한 체크
        if (!unit.getVideo().getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("권한이 없습니다.");
        }

        unit.setStatus(status);
        itemUnitRepository.save(unit);

        // 비디오의 전체 상태도 업데이트
        Video video = unit.getVideo();
        video.updateReservationStatus();
        videoRepository.save(video);
    }
}