package io.linsaftw.betterenderchest.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterEnderChestConfigMigrationTest {
    @Test
    void migratesLegacySixWhenNewValueIsAbsentAndRemovesLegacyValue() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("enderchest.rows", 6);

        assertTrue(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(6, config.getInt("storage.default-rows"));
        assertFalse(config.contains("enderchest.rows"));
    }

    @Test
    void newValueWinsAndLegacyValueIsRemoved() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("enderchest.rows", 6);
        config.set("storage.default-rows", 3);

        assertTrue(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(3, config.getInt("storage.default-rows"));
        assertFalse(config.contains("enderchest.rows"));
    }

    @Test
    void clampsInvalidNewValueToSix() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("storage.default-rows", 99);

        assertTrue(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(6, config.getInt("storage.default-rows"));
    }

    @Test
    void absentKeysDefaultToThree() {
        final YamlConfiguration config = new YamlConfiguration();

        assertTrue(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(3, config.getInt("storage.default-rows"));
    }

    @Test
    void legacyRowsSixMigratesToSix() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("enderchest.rows", 6);

        assertTrue(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(6, config.getInt("storage.default-rows"));
        assertFalse(config.contains("enderchest.rows"));
    }

    @Test
    void explicitDefaultRowsSixStaysSix() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("storage.default-rows", 6);

        assertFalse(BetterEnderChestConfig.migrateLegacyRows(config));

        assertEquals(6, config.getInt("storage.default-rows"));
    }

    @Test
    void oldScalarMaterialAndDisplayNameParse() throws Exception {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("controls.next-page.material", "ARROW");
        config.set("controls.next-page.display-name", "&eNext Page");

        final BetterEnderChestConfig wrapper = newWrapper(config);
        final ControlItemDefinition fallback = new ControlItemDefinition("STONE", "STONE", -1, "Fallback",
                Collections.emptyList());

        final ControlItemDefinition parsed = wrapper.getControlItem("next-page", fallback);

        assertEquals("ARROW", parsed.materialName());
        assertEquals("&eNext Page", parsed.name());
    }

    @Test
    void newNestedMaterialAndNameParse() throws Exception {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("controls.previous-page.material.modern", "ARROW");
        config.set("controls.previous-page.material.legacy", "ARROW");
        config.set("controls.previous-page.material.legacy-data", -1);
        config.set("controls.previous-page.name", "&ePrevious Page");
        config.set("controls.previous-page.lore", Arrays.asList("&7Go back"));

        final BetterEnderChestConfig wrapper = newWrapper(config);
        final ControlItemDefinition fallback = new ControlItemDefinition("STONE", "STONE", -1, "Fallback",
                Collections.emptyList());

        final ControlItemDefinition parsed = wrapper.getControlItem("previous-page", fallback);

        assertEquals("ARROW", parsed.modernMaterialName());
        assertEquals("ARROW", parsed.legacyMaterialName());
        assertEquals(-1, parsed.legacyData());
        assertEquals("&ePrevious Page", parsed.name());
        assertEquals(Arrays.asList("&7Go back"), parsed.lore());
        assertEquals("&ePrevious Page", wrapper.getControlItem("previous-page", "STONE", "Fallback").name());
    }

    @Test
    void staleAdminUsageDefaultIsMigratedToNewDefault() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("messages.admin-usage", "&cUsage: /ecadmin <player>");

        assertTrue(BetterEnderChestConfig.migrateAdminMessages(config));

        assertEquals("&cUsage: /ecadmin <player> [clear|view]",
                config.getString("messages.admin-usage"));
        assertEquals("&aCleared {player}'s enderchest.",
                config.getString("messages.admin-chest-cleared"));
        assertEquals("&cCannot clear {player}'s enderchest while it is open or loading. Close it and retry.",
                config.getString("messages.admin-clear-busy"));
    }

    @Test
    void customizedAdminUsageIsPreserved() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("messages.admin-usage", "&cCustom usage");
        config.set("messages.admin-chest-cleared", "&aCustom cleared");

        assertTrue(BetterEnderChestConfig.migrateAdminMessages(config));

        assertEquals("&cCustom usage", config.getString("messages.admin-usage"));
        assertEquals("&aCustom cleared", config.getString("messages.admin-chest-cleared"));
        assertEquals("&cCannot clear {player}'s enderchest while it is open or loading. Close it and retry.",
                config.getString("messages.admin-clear-busy"));
    }

    @Test
    void missingAdminMessageKeysGetDefaults() {
        final YamlConfiguration config = new YamlConfiguration();

        assertTrue(BetterEnderChestConfig.migrateAdminMessages(config));

        assertEquals("&cUsage: /ecadmin <player> [clear|view]",
                config.getString("messages.admin-usage"));
        assertEquals("&aCleared {player}'s enderchest.",
                config.getString("messages.admin-chest-cleared"));
        assertEquals("&cCannot clear {player}'s enderchest while it is open or loading. Close it and retry.",
                config.getString("messages.admin-clear-busy"));
    }

    @Test
    void currentAdminMessagesAreUntouched() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("messages.admin-usage", "&cUsage: /ecadmin <player> [clear|view]");
        config.set("messages.admin-chest-cleared", "&aCleared {player}'s enderchest.");
        config.set("messages.admin-clear-busy",
                "&cCannot clear {player}'s enderchest while it is open or loading. Close it and retry.");

        assertFalse(BetterEnderChestConfig.migrateAdminMessages(config));
    }

    private static BetterEnderChestConfig newWrapper(final YamlConfiguration config) throws Exception {
        final BetterEnderChestConfig wrapper = new BetterEnderChestConfig(null);
        final Field field = BetterEnderChestConfig.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(wrapper, config);
        return wrapper;
    }
}
