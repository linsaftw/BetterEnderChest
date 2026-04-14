package io.linsaftw.betterenderchest.service;

import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.folia.FoliaTasks;
import io.linsaftw.betterenderchest.inventory.ManagedEnderChestHolder;
import io.linsaftw.betterenderchest.storage.ChestRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EnderChestService {
    private final BetterEnderChestPlugin plugin;
    private final BetterEnderChestConfig pluginConfig;
    private final ChestRepository chestRepository;
    private final FoliaTasks foliaTasks;
    private final ConcurrentMap<UUID, Inventory> loadedChests;

    public EnderChestService(
            BetterEnderChestPlugin plugin,
            BetterEnderChestConfig pluginConfig,
            ChestRepository chestRepository,
            FoliaTasks foliaTasks) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.chestRepository = chestRepository;
        this.foliaTasks = foliaTasks;
        this.loadedChests = new ConcurrentHashMap<>();
    }

    public Inventory getOrLoad(Player player) {
        return getOrLoad(player.getUniqueId(), player.getName());
    }

    public Inventory getOrLoad(UUID ownerUuid, String ownerName) {
        return loadedChests.computeIfAbsent(ownerUuid, uuid -> {
            int size = pluginConfig.getInventorySize();
            String title = pluginConfig.getTitle();
            return chestRepository.load(ownerUuid, ownerName, size, title);
        });
    }

    public void openOwnChest(Player player) {
        Inventory chest = getOrLoad(player);
        player.openInventory(chest);
    }

    public void openAdminChest(Player viewer, UUID ownerUuid, String ownerName) {
        Inventory chest = getOrLoad(ownerUuid, ownerName);
        viewer.openInventory(chest);
    }

    public void migrateVanillaContentsIfNeeded(Player player) {
        UUID uuid = player.getUniqueId();
        if (!loadedChests.containsKey(uuid)) {
            return;
        }

        Inventory managedChest = loadedChests.get(uuid);
        ItemStack[] vanillaItems = player.getEnderChest().getContents();

        boolean hasVanillaItems = false;
        for (ItemStack item : vanillaItems) {
            if (item != null) {
                hasVanillaItems = true;
                break;
            }
        }

        boolean hasDoubleItems = false;
        for (ItemStack item : managedChest.getContents()) {
            if (item != null) {
                hasDoubleItems = true;
                break;
            }
        }

        if (hasVanillaItems && !hasDoubleItems) {
            for (int i = 0; i < vanillaItems.length && i < managedChest.getSize(); i++) {
                if (vanillaItems[i] != null) {
                    managedChest.setItem(i, vanillaItems[i]);
                }
            }
            player.getEnderChest().clear();
            save(uuid);
            if (pluginConfig.isDebugEnabled()) {
                plugin.getLogger().info("Migrated vanilla enderchest data for player: " + player.getName());
            }
        }
    }

    public void save(UUID ownerUuid) {
        Inventory chest = loadedChests.get(ownerUuid);
        if (chest == null) return;

        ItemStack[] snapshot = cloneContents(chest.getContents());

        foliaTasks.runAsync(() -> {
            String ownerName = getOwnerName(chest);
            chestRepository.save(ownerUuid, ownerName, snapshot);
        });
    }

    public void saveAll() {
        for (UUID uuid : loadedChests.keySet()) {
            Inventory chest = loadedChests.get(uuid);
            if (chest == null) continue;

            ItemStack[] snapshot = cloneContents(chest.getContents());
            String ownerName = getOwnerName(chest);

            foliaTasks.runAsync(() -> {
                chestRepository.save(uuid, ownerName, snapshot);
            });
        }
    }

    public void unload(UUID ownerUuid) {
        Inventory chest = loadedChests.get(ownerUuid);
        if (chest != null) {
            ItemStack[] snapshot = cloneContents(chest.getContents());
            String ownerName = getOwnerName(chest);
            loadedChests.remove(ownerUuid);

            foliaTasks.runAsync(() -> {
                chestRepository.save(ownerUuid, ownerName, snapshot);
            });
        }
    }

    public boolean isManagedInventory(Inventory inventory) {
        return inventory.getHolder() instanceof ManagedEnderChestHolder;
    }

    public UUID getManagedOwner(Inventory inventory) {
        if (inventory.getHolder() instanceof ManagedEnderChestHolder holder) {
            return holder.getOwnerUuid();
        }
        return null;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                clone[i] = contents[i].clone();
            }
        }
        return clone;
    }

    private String getOwnerName(Inventory chest) {
        if (chest.getHolder() instanceof ManagedEnderChestHolder holder) {
            return holder.getOwnerName();
        }
        return "unknown";
    }
}