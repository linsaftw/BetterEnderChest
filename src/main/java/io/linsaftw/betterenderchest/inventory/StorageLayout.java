package io.linsaftw.betterenderchest.inventory;

public final class StorageLayout {
    public static final int SLOTS_PER_ROW = 9;
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int PAGINATED_STORAGE_ROWS = 5;
    public static final int PREVIOUS_SLOT_OFFSET = 2;
    public static final int PAGE_INFO_SLOT_OFFSET = 4;
    public static final int NEXT_SLOT_OFFSET = 6;

    private final int totalRows;
    private final int totalSlots;
    private final int pageCount;

    private StorageLayout(final int totalRows) {
        if (totalRows <= 0 || totalRows > 54) {
            throw new IllegalArgumentException("rows must be between 1 and 54");
        }
        this.totalRows = totalRows;
        this.totalSlots = Math.multiplyExact(totalRows, SLOTS_PER_ROW);
        this.pageCount = totalRows <= MAX_VISIBLE_ROWS
                ? 1
                : (totalRows + PAGINATED_STORAGE_ROWS - 1) / PAGINATED_STORAGE_ROWS;
    }

    public static StorageLayout forRows(final int rows) {
        return new StorageLayout(rows);
    }

    public int totalRows() {
        return totalRows;
    }

    public int totalSlots() {
        return totalSlots;
    }

    public int pageCount() {
        return pageCount;
    }

    public Page page(final int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("page index out of range: " + pageIndex);
        }
        int storageRows = totalRows <= MAX_VISIBLE_ROWS
                ? totalRows
                : Math.min(PAGINATED_STORAGE_ROWS, totalRows - pageIndex * PAGINATED_STORAGE_ROWS);
        int storageOffset = totalRows <= MAX_VISIBLE_ROWS
                ? 0
                : Math.multiplyExact(pageIndex * PAGINATED_STORAGE_ROWS, SLOTS_PER_ROW);
        int storageSlots = Math.multiplyExact(storageRows, SLOTS_PER_ROW);
        int inventoryRows = totalRows <= MAX_VISIBLE_ROWS ? storageRows : storageRows + 1;
        int controlRowStart = totalRows <= MAX_VISIBLE_ROWS ? -1 : storageSlots;
        return new Page(pageIndex, pageCount, storageOffset, storageSlots, inventoryRows, controlRowStart);
    }

    public record Page(int pageIndex, int pageCount, int storageOffset, int storageSlots,
                       int inventoryRows, int controlRowStart) {
        public boolean paginated() {
            return pageCount > 1;
        }

        public boolean hasPrevious() {
            return paginated() && pageIndex > 0;
        }

        public boolean hasNext() {
            return paginated() && pageIndex + 1 < pageCount;
        }

        public int previousSlot() {
            return controlRowStart < 0 ? -1 : controlRowStart + PREVIOUS_SLOT_OFFSET;
        }

        public int pageInfoSlot() {
            return controlRowStart < 0 ? -1 : controlRowStart + PAGE_INFO_SLOT_OFFSET;
        }

        public int nextSlot() {
            return controlRowStart < 0 ? -1 : controlRowStart + NEXT_SLOT_OFFSET;
        }

        public boolean isControlSlot(final int inventorySlot) {
            return controlRowStart >= 0
                    && inventorySlot >= controlRowStart
                    && inventorySlot < controlRowStart + SLOTS_PER_ROW;
        }
    }
}
