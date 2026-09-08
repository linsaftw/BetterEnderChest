package io.linsaftw.betterenderchest.config;

public record StorageTier(String id, String permission, int rows) {
    public StorageTier {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission must not be blank");
        }
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be greater than zero");
        }
    }
}
