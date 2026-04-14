package io.linsaftw.betterenderchest.storage;

import io.linsaftw.betterenderchest.inventory.ManagedEnderChestHolder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

public class YamlChestRepository implements ChestRepository {
    private final JavaPlugin plugin;
    private final File dataFolder;

    public YamlChestRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "enderchests");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    @Override
    public org.bukkit.inventory.Inventory load(UUID ownerUuid, String ownerName, int size, String title) {
        org.bukkit.inventory.Inventory inventory = Bukkit.createInventory(
                new ManagedEnderChestHolder(ownerUuid, ownerName),
                size,
                title
        );

        File playerFile = new File(dataFolder, ownerUuid.toString() + ".yml");
        if (playerFile.exists()) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                for (int i = 0; i < size; i++) {
                    if (config.contains("items." + i)) {
                        ItemStack item = config.getItemStack("items." + i);
                        if (item != null) {
                            inventory.setItem(i, item);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading enderchest for " + ownerName + ": " + e.getMessage());
            }
        }

        return inventory;
    }

    @Override
    public void save(UUID ownerUuid, String ownerName, ItemStack[] contents) {
        File playerFile = new File(dataFolder, ownerUuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("player-name", ownerName);
        config.set("last-saved", System.currentTimeMillis());

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null) {
                config.set("items." + i, item);
            }
        }

        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving enderchest for " + ownerUuid + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory for: " + playerFile.getAbsolutePath(), e);
        }
    }

    @Override
    public boolean exists(UUID ownerUuid) {
        File playerFile = new File(dataFolder, ownerUuid.toString() + ".yml");
        return playerFile.exists();
    }
}