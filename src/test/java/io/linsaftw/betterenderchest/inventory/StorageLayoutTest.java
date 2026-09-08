package io.linsaftw.betterenderchest.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageLayoutTest {
    @Test
    void sixRowsHaveNoControls() {
        final StorageLayout.Page page = StorageLayout.forRows(6).page(0);

        assertEquals(1, page.pageCount());
        assertEquals(0, page.storageOffset());
        assertEquals(54, page.storageSlots());
        assertEquals(6, page.inventoryRows());
        assertEquals(-1, page.controlRowStart());
        assertFalse(page.paginated());
        assertEquals(-1, page.previousSlot());
        assertEquals(-1, page.pageInfoSlot());
        assertEquals(-1, page.nextSlot());
        assertFalse(page.isControlSlot(0));
    }

    @Test
    void sevenRowsUseSafePaginatedPages() {
        final StorageLayout layout = StorageLayout.forRows(7);
        final StorageLayout.Page first = layout.page(0);
        final StorageLayout.Page last = layout.page(1);

        assertEquals(2, layout.pageCount());
        assertEquals(45, first.storageSlots());
        assertEquals(6, first.inventoryRows());
        assertEquals(0, first.storageOffset());
        assertEquals(45, first.controlRowStart());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        assertEquals(47, first.previousSlot());
        assertEquals(49, first.pageInfoSlot());
        assertEquals(51, first.nextSlot());
        assertEquals(18, last.storageSlots());
        assertEquals(3, last.inventoryRows());
        assertEquals(45, last.storageOffset());
        assertFalse(last.hasNext());
        assertTrue(last.hasPrevious());
    }

    @Test
    void rankRowsHaveExpectedPageCounts() {
        for (int rows = 10; rows <= 35; rows += 5) {
            assertEquals(rows / 5, StorageLayout.forRows(rows).pageCount());
        }
    }

    @Test
    void controlsUseOffsetsTwoFourAndSix() {
        final StorageLayout.Page page = StorageLayout.forRows(10).page(0);

        assertEquals(StorageLayout.PREVIOUS_SLOT_OFFSET,
                page.previousSlot() - page.controlRowStart());
        assertEquals(StorageLayout.PAGE_INFO_SLOT_OFFSET,
                page.pageInfoSlot() - page.controlRowStart());
        assertEquals(StorageLayout.NEXT_SLOT_OFFSET,
                page.nextSlot() - page.controlRowStart());
    }

    @Test
    void fullControlRowIsControl() {
        final StorageLayout.Page page = StorageLayout.forRows(10).page(0);

        assertEquals(45, page.controlRowStart());
        for (int slot = 45; slot <= 53; slot++) {
            assertTrue(page.isControlSlot(slot), "slot " + slot + " should be control");
        }
        assertFalse(page.isControlSlot(44));
        assertFalse(page.isControlSlot(54));
    }

    @Test
    void firstPageHasNoPreviousAndLastPageHasNoNext() {
        final StorageLayout layout = StorageLayout.forRows(10);

        assertFalse(layout.page(0).hasPrevious());
        assertTrue(layout.page(0).hasNext());
        assertFalse(layout.page(layout.pageCount() - 1).hasNext());
        assertTrue(layout.page(layout.pageCount() - 1).hasPrevious());
    }

    @Test
    void sixRowPageHasNoControlSlots() {
        final StorageLayout.Page page = StorageLayout.forRows(6).page(0);

        for (int slot = 0; slot < 54; slot++) {
            assertFalse(page.isControlSlot(slot), "slot " + slot + " should not be control");
        }
    }

    @Test
    void fiftyRowsHaveTenPages() {
        assertEquals(10, StorageLayout.forRows(50).pageCount());
    }
}
