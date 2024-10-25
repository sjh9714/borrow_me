package com.ardkyer.rion.entity;

public enum ItemStatus {
    AVAILABLE("예약가능"),
    RESERVED("예약중"),
    IN_USE("사용중");

    private final String displayName;

    ItemStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}