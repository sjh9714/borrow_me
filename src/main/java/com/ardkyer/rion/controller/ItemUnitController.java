package com.ardkyer.rion.controller;

import com.ardkyer.rion.entity.ItemStatus;
import com.ardkyer.rion.entity.Video;
import com.ardkyer.rion.service.VideoService;
import com.ardkyer.rion.service.ItemUnitService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/items/units")
public class ItemUnitController {

    @Autowired
    private ItemUnitService itemUnitService;

    @Autowired
    private VideoService videoService;

    @Getter @Setter
    public static class StatusUpdateRequest {
        private String status;
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUnitStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request,
            Authentication authentication) {
        try {
            ItemStatus newStatus = ItemStatus.valueOf(request.getStatus());
            itemUnitService.updateUnitStatus(id, newStatus, authentication);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "상태가 변경되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "잘못된 상태값입니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}