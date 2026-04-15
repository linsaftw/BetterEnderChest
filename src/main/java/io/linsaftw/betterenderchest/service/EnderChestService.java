package io.linsaftw.betterenderchest.service;

import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.inventory.ManagedEnderChestHolder;
import io.linsaftw.betterenderchest.storage.ChestRepository;
import io.linsaftw.betterenderchest.utils.FoliaAPI;
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
    private final FoliaAPI foliaApi;
    private final ConcurrentMap<UUID, Inventory> loadedChests;
    private final ConcurrentMap<UUID, UUID> activeViewerByChest;

    public EnderChestService(
            BetterEnderChestPlugin plugin,
            BetterEnderChestConfig pluginConfig,
            ChestRepository chestRepository,
            FoliaAPI foliaApi) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.chestRepository = chestRepository;
        this.foliaApi = foliaApi;
        this.loadedChests = new ConcurrentHashMap<>();
        this.activeViewerByChest = new ConcurrentHashMap<>();
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

    public boolean openOwnChest(Player player) {
        return openChestForViewer(player, player.getUniqueId(), player.getName());
    }

    public boolean openAdminChest(Player viewer, UUID ownerUuid, String ownerName) {
        return openChestForViewer(viewer, ownerUuid, ownerName);
    }

    private boolean openChestForViewer(Player viewer, UUID ownerUuid, String ownerName) {
        UUID existingViewer = activeViewerByChest.get(ownerUuid);
        if (existingViewer != null && !existingViewer.equals(viewer.getUniqueId())) {
            Player currentViewer = Bukkit.getPlayer(existingViewer);
            if (currentViewer != null && currentViewer.isOnline()) {
                return false;
            }
        }
        activeViewerByChest.put(ownerUuid, viewer.getUniqueId());
        Inventory chest = getOrLoad(ownerUuid, ownerName);
        viewer.openInventory(chest);
        return true;
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

        Player activeViewer = getActiveViewer(ownerUuid);
        if (activeViewer != null) {
            foliaApi.runEntity(activeViewer, () -> snapshotAndSave(ownerUuid, chest));
        } else {
            snapshotAndSave(ownerUuid, chest);
        }
    }

    public void scheduleAutoSavePass() {
        for (UUID uuid : loadedChests.keySet()) {
            save(uuid);
        }
    }

    public void saveAll() {
        scheduleAutoSavePass();
    }

    public void saveAllNow() {
        for (UUID uuid : loadedChests.keySet()) {
            Inventory chest = loadedChests.get(uuid);
            if (chest == null) continue;

            ItemStack[] snapshot = cloneContents(chest.getContents());
            String ownerName = getOwnerName(chest);
            chestRepository.save(uuid, ownerName, snapshot);
        }
    }

    public void unload(UUID ownerUuid) {
        Inventory chest = loadedChests.get(ownerUuid);
        if (chest != null) {
            activeViewerByChest.remove(ownerUuid);
            ItemStack[] snapshot = cloneContents(chest.getContents());
            String ownerName = getOwnerName(chest);
            loadedChests.remove(ownerUuid);

            foliaApi.runAsync(() -> {
                chestRepository.save(ownerUuid, ownerName, snapshot);
            });
        }
    }

    public void releaseViewer(final UUID ownerUuid, final UUID viewerUuid) {
        activeViewerByChest.remove(ownerUuid, viewerUuid);
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

    private Player getActiveViewer(final UUID ownerUuid) {
        UUID viewerUuid = activeViewerByChest.get(ownerUuid);
        if (viewerUuid == null) return null;
        Player player = Bukkit.getPlayer(viewerUuid);
        if (player == null || !player.isOnline()) return null;
        return player;
    }

    private void snapshotAndSave(final UUID ownerUuid, final Inventory chest) {
        ItemStack[] snapshot = cloneContents(chest.getContents());
        String ownerName = getOwnerName(chest);
        chestRepository.save(ownerUuid, ownerName, snapshot);
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
