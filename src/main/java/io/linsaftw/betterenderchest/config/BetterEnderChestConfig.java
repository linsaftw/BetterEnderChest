package io.linsaftw.betterenderchest.config;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

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
        int rows = config.getInt("enderchest.rows", 6);
        if (rows < 1) {
            plugin.getLogger().warning("Invalid rows value (" + rows + "), setting to minimum (1)");
            config.set("enderchest.rows", 1);
        } else if (rows > 6) {
            plugin.getLogger().warning("Invalid rows value (" + rows + "), setting to maximum (6)");
            config.set("enderchest.rows", 6);
        }

        int saveInterval = config.getInt("enderchest.save-interval-seconds", 300);
        if (saveInterval <= 0) {
            plugin.getLogger().warning("Invalid save-interval-seconds value (" + saveInterval + "), setting to default (300)");
            config.set("enderchest.save-interval-seconds", 300);
        }

        plugin.saveConfig();
    }

    public int getRows() {
        int rows = config.getInt("enderchest.rows", 6);
        if (rows < 1) return 1;
        if (rows > 6) return 6;
        return rows;
    }

    public int getInventorySize() {
        return getRows() * 9;
    }

    public String getTitle() {
        String title = config.getString("enderchest.title", "enderchest");
        return translateColor(title);
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

    public String getMessage(String key) {
        String msg = config.getString("messages." + key, "&cMissing message: " + key);
        return translateColor(msg);
    }

    public String getMessage(String key, String placeholder, String value) {
        String msg = getMessage(key);
        return msg.replace("{" + placeholder + "}", value);
    }

    private String translateColor(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}