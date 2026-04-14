package io.linsaftw.betterenderchest.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class ManagedEnderChestHolder implements InventoryHolder {
    private final UUID ownerUuid;
    private final String ownerName;

    public ManagedEnderChestHolder(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }
}