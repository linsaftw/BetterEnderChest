package io.linsaftw.betterenderchest.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BetterEnderChestConfig {
    private final JavaPlugin plugin;
    private org.bukkit.configuration.file.FileConfiguration config;

    public BetterEnderChestConfig(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        validateConfig();
    }

    private void validateConfig() {
        migrateLegacyRows(config);

        final int defaultRows = config.getInt("storage.default-rows", 3);
        if (defaultRows < 1 || defaultRows > 6) {
            final int fallbackRows = Math.max(1, Math.min(defaultRows, 6));
            plugin.getLogger().warning("Invalid storage.default-rows value (" + defaultRows
                    + "), setting to " + fallbackRows);
            config.set("storage.default-rows", fallbackRows);
        }

        for (final String tier : Arrays.asList("special", "mvp", "elite", "mythical", "celestial", "immortal", "patron")) {
            final String path = "storage.tiers." + tier + ".rows";
            if (config.contains(path)) {
                final int rows = config.getInt(path, 1);
                if (rows < 1 || rows > 54) {
                    final int clamped = Math.max(1, Math.min(rows, 54));
                    plugin.getLogger().warning("Invalid storage.tiers." + tier + ".rows value (" + rows
                            + "), setting to " + clamped);
                    config.set(path, clamped);
                }
            }
        }

        int saveInterval = config.getInt("enderchest.save-interval-seconds", 300);
        if (saveInterval <= 0) {
            plugin.getLogger().warning("Invalid save-interval-seconds value (" + saveInterval + "), setting to default (300)");
            config.set("enderchest.save-interval-seconds", 300);
        }

        migrateAdminMessages(config);

        plugin.saveConfig();
    }

    static boolean migrateLegacyRows(final FileConfiguration config) {
        boolean changed = false;
        if (!config.contains("storage.default-rows") && config.contains("enderchest.rows")) {
            final int legacyRows = config.getInt("enderchest.rows", 3);
            config.set("storage.default-rows", Math.max(1, Math.min(legacyRows, 6)));
            changed = true;
        }
        if (config.contains("enderchest.rows")) {
            config.set("enderchest.rows", null);
            changed = true;
        }

        final int defaultRows = config.getInt("storage.default-rows", 3);
        if (!config.contains("storage.default-rows")) {
            config.set("storage.default-rows", 3);
            changed = true;
        } else if (defaultRows < 1 || defaultRows > 6) {
            config.set("storage.default-rows", Math.max(1, Math.min(defaultRows, 6)));
            changed = true;
        }
        return changed;
    }

    static final String LEGACY_ADMIN_USAGE = "&cUsage: /ecadmin <player>";
    static final String ADMIN_USAGE_DEFAULT = "&cUsage: /ecadmin <player> [clear|view]";
    static final String ADMIN_CHEST_CLEARED_DEFAULT = "&aCleared {player}'s enderchest.";
    static final String ADMIN_CLEAR_BUSY_DEFAULT = "&cCannot clear {player}'s enderchest while it is open or loading. Close it and retry.";

    static boolean migrateAdminMessages(final FileConfiguration config) {
        boolean changed = false;
        if (!config.contains("messages.admin-usage")) {
            config.set("messages.admin-usage", ADMIN_USAGE_DEFAULT);
            changed = true;
        } else if (LEGACY_ADMIN_USAGE.equals(config.getString("messages.admin-usage"))) {
            config.set("messages.admin-usage", ADMIN_USAGE_DEFAULT);
            changed = true;
        }
        if (!config.contains("messages.admin-chest-cleared")) {
            config.set("messages.admin-chest-cleared", ADMIN_CHEST_CLEARED_DEFAULT);
            changed = true;
        }
        if (!config.contains("messages.admin-clear-busy")) {
            config.set("messages.admin-clear-busy", ADMIN_CLEAR_BUSY_DEFAULT);
            changed = true;
        }
        return changed;
    }

    public int getStorageDefaultRows() {
        int rows = config.getInt("storage.default-rows", 3);
        if (rows < 1) return 1;
        if (rows > 6) return 6;
        return rows;
    }

    public List<StorageTier> getStorageTiers() {
        return Arrays.asList(
                getTier("special", 6),
                getTier("mvp", 10),
                getTier("elite", 15),
                getTier("mythical", 20),
                getTier("celestial", 25),
                getTier("immortal", 30),
                getTier("patron", 35));
    }

    private StorageTier getTier(final String tier, final int fallbackRows) {
        final String fallbackPermission = "betterenderchest.rows." + tier;
        final String permission = config.getString("storage.tiers." + tier + ".permission", fallbackPermission);
        return new StorageTier(tier, permission, getTierRows(tier, fallbackRows));
    }

    private int getTierRows(final String tier, final int fallback) {
        final int rows = config.getInt("storage.tiers." + tier + ".rows", fallback);
        if (rows < 1) return 1;
        if (rows > 54) return 54;
        return rows;
    }

    public ControlItemDefinition getControlItem(final String key, final ControlItemDefinition fallback) {
        final String path = "controls." + key;
        if (config == null || !config.contains(path)) {
            return fallback;
        }
        final String modern;
        final String legacy;
        final int legacyData;
        if (config.contains(path + ".material.modern") || config.contains(path + ".material.legacy")
                || config.contains(path + ".material.legacy-data")) {
            modern = config.getString(path + ".material.modern",
                    fallback == null ? "" : fallback.modernMaterialName());
            legacy = config.getString(path + ".material.legacy",
                    fallback == null ? "" : fallback.legacyMaterialName());
            legacyData = config.getInt(path + ".material.legacy-data",
                    fallback == null ? -1 : fallback.legacyData());
        } else {
            final String scalar = config.getString(path + ".material",
                    fallback == null ? "" : fallback.materialName());
            modern = scalar;
            legacy = scalar;
            legacyData = fallback == null ? -1 : fallback.legacyData();
        }
        String name = config.getString(path + ".name", null);
        if (name == null) {
            name = config.getString(path + ".display-name",
                    fallback == null ? "" : fallback.name());
        }
        if (name == null) {
            name = fallback == null ? "" : fallback.name();
        }
        List<String> lore = config.getStringList(path + ".lore");
        if ((lore == null || lore.isEmpty()) && fallback != null) {
            lore = fallback.lore();
        }
        if (lore == null) {
            lore = Collections.emptyList();
        }
        try {
            return new ControlItemDefinition(
                    modern == null ? "" : modern,
                    legacy == null ? "" : legacy,
                    legacyData,
                    name,
                    lore);
        } catch (final IllegalArgumentException ex) {
            return fallback;
        }
    }

    public ControlItemDefinition getControlItem(final String key, final String fallbackMaterial,
                                                final String fallbackDisplayName) {
        final ControlItemDefinition fallback = new ControlItemDefinition(
                fallbackMaterial == null ? "" : fallbackMaterial,
                fallbackMaterial == null ? "" : fallbackMaterial,
                -1,
                fallbackDisplayName == null ? "" : fallbackDisplayName,
                Collections.emptyList());
        return getControlItem(key, fallback);
    }

    public String getInventoryTitleTemplate() {
        return config.getString("enderchest.title", "Better Ender Chest (Page {page}/{pages})");
    }

    public String getTitle() {
        return getInventoryTitleTemplate();
    }

    public boolean isAutoSaveEnabled() {
        return config.getBoolean("enderchest.auto-save", true);
    }

    public long getAutoSaveIntervalTicks() {
        int seconds = config.getInt("enderchest.save-interval-seconds", 300);
        if (seconds <= 0) return 300 * 20L;
        return Math.max(seconds * 20L, 20L);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public String getMessageSource(final String key) {
        return config.getString("messages." + key, "");
    }

    public String getMessage(String key) {
        return getMessageSource(key);
    }

    public String getMessage(String key, String placeholder, String value) {
        String msg = getMessage(key);
        return msg.replace("{" + placeholder + "}", value);
    }
}
