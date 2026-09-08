package io.linsaftw.betterenderchest.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialsTest {

    @Test
    void absentModernFallsBackToLegacy() {
        final ItemStack item = Materials.create("BETTERECHEST_NO_SUCH_MODERN", "DIAMOND", -1);

        assertEquals(Material.DIAMOND, item.getType());
    }

    @Test
    void grayPaneUsesLegacyData() {
        final ItemStack item = Materials.create("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 7);

        assertEquals(Material.STAINED_GLASS_PANE, item.getType());
        assertEquals(7, item.getDurability());
    }

    @Test
    void missingAllThrows() {
        assertThrows(IllegalStateException.class,
                () -> Materials.create("BETTERECHEST_NO_SUCH_MODERN", "BETTERECHEST_NO_SUCH_LEGACY", -1));
    }
}
