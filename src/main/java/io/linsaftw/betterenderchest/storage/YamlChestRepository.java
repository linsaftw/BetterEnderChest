package io.linsaftw.betterenderchest.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

public class YamlChestRepository implements ChestRepository {
    private final Logger logger;
    private final Path dataFolder;

    public YamlChestRepository(final Logger logger, final Path dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Override
    public ChestData load(final UUID ownerUuid, final String ownerName) {
        final Path playerFile = playerFile(ownerUuid);
        if (!Files.exists(playerFile)) {
            return ChestData.empty(ownerUuid, ownerName, 0);
        }

        try {
            final FileConfiguration config = new YamlConfiguration();
            config.loadFromString(Files.readString(playerFile, StandardCharsets.UTF_8));
            final String storedName = config.getString("player-name", ownerName);
            final String loadedName = storedName == null ? ownerName : storedName;
            int rows = config.getInt("last-known-rows", 0);
            if (rows < 0) {
                logger.warning("Ignoring negative last-known-rows for " + ownerUuid);
                rows = 0;
            }

            final java.util.Map<Integer, ItemStack> items = new java.util.HashMap<>();
            int highestSlot = -1;
            if (config.isConfigurationSection("items")) {
                for (final String key : config.getConfigurationSection("items").getKeys(false)) {
                    final int slot;
                    try {
                        slot = Integer.parseInt(key);
                        if (slot < 0) {
                            throw new NumberFormatException("negative slot");
                        }
                    } catch (NumberFormatException exception) {
                        logger.warning("Ignoring malformed chest slot " + key + " for " + ownerUuid);
                        continue;
                    }
                    final ItemStack item;
                    try {
                        item = config.getItemStack("items." + key);
                    } catch (Exception exception) {
                        logger.warning("Ignoring malformed chest item " + key + " for " + ownerUuid);
                        continue;
                    }
                    if (item == null) {
                        logger.warning("Ignoring malformed chest item " + key + " for " + ownerUuid);
                        continue;
                    }
                    items.put(slot, item);
                    highestSlot = Math.max(highestSlot, slot);
                }
            }

            final int slotCount = Math.max(Math.multiplyExact(rows, 9),
                    highestSlot == Integer.MAX_VALUE ? Integer.MAX_VALUE : highestSlot + 1);
            final ItemStack[] contents = new ItemStack[slotCount];
            for (final java.util.Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                contents[entry.getKey()] = entry.getValue();
            }
            return new ChestData(ownerUuid, loadedName, rows, contents);
        } catch (Exception exception) {
            throw new ChestStorageException("Failed to load chest for " + ownerUuid, exception);
        }
    }

    @Override
    public void save(final ChestData data) {
        final Path playerFile = playerFile(data.ownerUuid());
        Path temporaryFile = null;
        try {
            Files.createDirectories(dataFolder);
            final FileConfiguration config = new YamlConfiguration();

            config.set("data-version", 2);
            config.set("player-name", data.ownerName());
            config.set("last-saved", System.currentTimeMillis());
            config.set("last-known-rows", data.lastKnownRows());

            final ItemStack[] contents = data.copyContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    config.set("items." + i, contents[i]);
                }
            }

            temporaryFile = Files.createTempFile(dataFolder, playerFile.getFileName().toString(), ".tmp");
            Files.writeString(temporaryFile, config.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, playerFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new ChestStorageException("Failed to save chest for " + data.ownerUuid(), exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (Exception ignored) {
                    // Best-effort cleanup; preserve the original save result.
                }
            }
        }
    }

    @Override
    public boolean exists(final UUID ownerUuid) {
        return Files.exists(playerFile(ownerUuid));
    }

    private Path playerFile(final UUID ownerUuid) {
        return dataFolder.resolve(ownerUuid.toString() + ".yml");
    }
}
