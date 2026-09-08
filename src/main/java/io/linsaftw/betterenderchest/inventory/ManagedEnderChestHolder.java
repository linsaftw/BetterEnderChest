package io.linsaftw.betterenderchest.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class ManagedEnderChestHolder implements InventoryHolder {
    private final UUID ownerUuid;
    private final String ownerName;
    private final int pageIndex;
    private final int pageCount;
    private final int storageOffset;
    private final int storageCount;
    private final int controlRowStart;
    private volatile Inventory inventory;

    public ManagedEnderChestHolder(final UUID ownerUuid, final String ownerName,
                                   final int pageIndex, final int pageCount, final int storageOffset,
                                   final int storageCount, final int controlRowStart) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.pageIndex = pageIndex;
        this.pageCount = pageCount;
        this.storageOffset = storageOffset;
        this.storageCount = storageCount;
        this.controlRowStart = controlRowStart;
    }

    @Override
    public Inventory getInventory() {
        final Inventory boundInventory = inventory;
        if (boundInventory == null) {
            throw new IllegalStateException("Inventory has not been bound");
        }
        return boundInventory;
    }

    public synchronized void bindInventory(final Inventory inventory) {
        if (inventory == null) {
            throw new NullPointerException("inventory");
        }
        if (this.inventory != null) {
            throw new IllegalStateException("Inventory has already been bound");
        }
        this.inventory = inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getStorageOffset() {
        return storageOffset;
    }

    public int getStorageCount() {
        return storageCount;
    }

    public int getControlRowStart() {
        return controlRowStart;
    }

    public boolean isPaginated() {
        return pageCount > 1;
    }

    public boolean isControlSlot(final int inventorySlot) {
        return controlRowStart >= 0
                && inventorySlot >= controlRowStart
                && inventorySlot < controlRowStart + StorageLayout.SLOTS_PER_ROW;
    }
}
