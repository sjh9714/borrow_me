package com.ardkyer.rion.repository;

import com.ardkyer.rion.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemUnitRepository extends JpaRepository<ItemUnit, Long> {
    List<ItemUnit> findByVideoAndStatus(Video video, ItemStatus status);
    List<ItemUnit> findByVideo(Video video);
}