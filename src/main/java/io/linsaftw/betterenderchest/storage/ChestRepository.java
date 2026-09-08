package io.linsaftw.betterenderchest.storage;

import java.util.UUID;

public interface ChestRepository {
    ChestData load(UUID ownerUuid, String ownerName);
    void save(ChestData data);
    boolean exists(UUID ownerUuid);
}
