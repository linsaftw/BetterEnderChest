package io.linsaftw.betterenderchest.storage;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ChestData {
    private final UUID ownerUuid;
    private final String ownerName;
    private final int lastKnownRows;
    private final ItemStack[] contents;

    public ChestData(final UUID ownerUuid, final String ownerName, final int lastKnownRows,
                     final ItemStack[] contents) {
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        if (lastKnownRows < 0) {
            throw new IllegalArgumentException("lastKnownRows must not be negative");
        }
        this.lastKnownRows = lastKnownRows;
        this.contents = cloneContents(Objects.requireNonNull(contents, "contents"));
    }

    public static ChestData empty(final UUID ownerUuid, final String ownerName, final int rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("rows must not be negative");
        }
        return new ChestData(ownerUuid, ownerName, rows,
                new ItemStack[Math.multiplyExact(rows, 9)]);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public int lastKnownRows() {
        return lastKnownRows;
    }

    public ItemStack[] copyContents() {
        return cloneContents(contents);
    }

    public int slotCount() {
        return contents.length;
    }

    public int occupiedRows() {
        int highest = -1;
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) {
                highest = slot;
            }
        }
        return highest < 0 ? 0 : (highest + 9) / 9;
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        ItemStack[] copy = Arrays.copyOf(source, source.length);
        for (int slot = 0; slot < copy.length; slot++) {
            if (copy[slot] != null) {
                copy[slot] = copy[slot].clone();
            }
        }
        return copy;
    }
}
