package io.linsaftw.betterenderchest.storage;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface ChestRepository {
    org.bukkit.inventory.Inventory load(UUID ownerUuid, String ownerName, int size, String title);
    void save(UUID ownerUuid, String ownerName, ItemStack[] contents);
    boolean exists(UUID ownerUuid);
}