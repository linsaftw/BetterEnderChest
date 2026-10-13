package io.linsaftw.betterenderchest.service;

import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.config.ControlItemDefinition;
import io.linsaftw.betterenderchest.inventory.ManagedEnderChestHolder;
import io.linsaftw.betterenderchest.inventory.StorageLayout;
import io.linsaftw.betterenderchest.storage.ChestData;
import io.linsaftw.betterenderchest.storage.ChestPersistenceService;
import io.linsaftw.betterenderchest.utils.FoliaAPI;
import io.linsaftw.betterenderchest.utils.Materials;
import io.linsaftw.betterenderchest.text.TextBridge;
import io.linsaftw.betterenderchest.text.TextCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class EnderChestService {
    public enum OpenResult { STARTED, SHUTTING_DOWN, BUSY }
    public enum ClearResult { CLEARED, BUSY, SHUTTING_DOWN }

    private final BetterEnderChestPlugin plugin;
    private final BetterEnderChestConfig config;
    private final PermissionService permissions;
    private final ChestPersistenceService persistence;
    private final FoliaAPI folia;
    private volatile TextCodec textCodec;
    private volatile TextBridge textBridge;
    private final ConcurrentMap<UUID, OwnerChestSession> owners = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> ownerByViewer = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;

    public EnderChestService(final BetterEnderChestPlugin plugin, final BetterEnderChestConfig config,
                             final PermissionService permissions, final ChestPersistenceService persistence,
                             final FoliaAPI folia) {
        this.plugin = plugin;
        this.config = config;
        this.permissions = permissions;
        this.persistence = persistence;
        this.folia = folia;
    }

    public void setTextCodec(final TextCodec codec) { this.textCodec = codec; }
    public void setTextBridge(final TextBridge bridge) { this.textBridge = bridge; }

    public OpenResult openOwnChest(final Player player) {
        if (shuttingDown) return OpenResult.SHUTTING_DOWN;
        final int authRows = clampRows(permissions.getStorageRows(player));
        final OwnerChestSession owner = getOrCreateOwnerSession(player.getUniqueId(), player.getName(), authRows);
        loadOwnerOnce(owner, player.getName());
        synchronized (owner) {
            if (owner.isClearing()) return OpenResult.BUSY;
            reconcileVisibleRows(owner, authRows, false);
        }
        return openViewerPage(player, owner, 0);
    }

    public OpenResult openAdminChest(final Player viewer, final UUID ownerUuid, final String ownerName) {
        if (shuttingDown) return OpenResult.SHUTTING_DOWN;
        final String safeName = ownerName == null ? ownerUuid.toString() : ownerName;
        final Player target = Bukkit.getPlayer(ownerUuid);
        if (target != null && target.isOnline()) {
            folia.callEntity(target, new java.util.function.Supplier<Integer>() {
                @Override public Integer get() { return clampRows(permissions.getStorageRows(target)); }
            }).whenComplete((rows, failure) -> {
                if (shuttingDown || failure != null) return;
                final int liveRows = rows == null ? config.getStorageDefaultRows() : clampRows(rows.intValue());
                final OwnerChestSession owner = getOrCreateOwnerSession(ownerUuid, safeName, liveRows);
                synchronized (owner) {
                    if (shuttingDown || owner.isClearing()) return;
                    owner.setOwnerName(target.getName() == null ? safeName : target.getName());
                    loadOwnerOnce(owner, owner.getOwnerName());
                    reconcileVisibleRows(owner, liveRows, false);
                }
                folia.runEntity(viewer, new Runnable() {
                    @Override public void run() {
                        if (!shuttingDown && viewer.isOnline()) openViewerPage(viewer, owner, 0);
                    }
                });
            });
            return OpenResult.STARTED;
        }
        final OwnerChestSession owner = getOrCreateOwnerSession(ownerUuid, safeName, config.getStorageDefaultRows());
        loadOwnerOnce(owner, safeName);
        folia.runAsync(new Runnable() {
            @Override public void run() {
                try {
                    final ChestData data = owner.getLoadFuture() == null ? null : owner.getLoadFuture().get();
                    final int recovery = data == null
                            ? config.getStorageDefaultRows()
                            : Math.max(config.getStorageDefaultRows(),
                                Math.max(data.lastKnownRows(), data.occupiedRows()));
                    synchronized (owner) {
                        if (!shuttingDown && !owner.isClearing()) reconcileVisibleRows(owner, clampRows(recovery), true);
                    }
                } catch (Exception ignored) {
                    synchronized (owner) {
                        if (!shuttingDown && !owner.isClearing()) {
                            reconcileVisibleRows(owner, config.getStorageDefaultRows(), true);
                        }
                    }
                }
                folia.runEntity(viewer, new Runnable() {
                    @Override public void run() {
                        if (!shuttingDown && viewer.isOnline()) openViewerPage(viewer, owner, 0);
                    }
                });
            }
        });
        return OpenResult.STARTED;
    }

    public CompletableFuture<ClearResult> clearChest(final UUID ownerUuid, final String ownerName) {
        if (ownerUuid == null) {
            final CompletableFuture<ClearResult> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new IllegalArgumentException("ownerUuid must not be null"));
            return rejected;
        }
        if (shuttingDown) return CompletableFuture.completedFuture(ClearResult.SHUTTING_DOWN);
        final String safeName = ownerName == null ? ownerUuid.toString() : ownerName;
        final OwnerChestSession owner = getOrCreateOwnerSession(ownerUuid, safeName,
                config.getStorageDefaultRows());
        final Optional<OwnerInteractionGate.Lease> admin = owner.getInteractionGate()
                .tryAcquire(UUID.randomUUID(), UUID.randomUUID());
        if (!admin.isPresent()) return CompletableFuture.completedFuture(ClearResult.BUSY);
        final OwnerInteractionGate.Lease lease = admin.get();
        final int minRows;
        final String clearName;
        synchronized (owner) {
            if (owner.isClearing() || !owner.getViewers().isEmpty() || owner.isLoading()) {
                owner.getInteractionGate().release(lease);
                return CompletableFuture.completedFuture(ClearResult.BUSY);
            }
            CompletableFuture<ChestData> loadFuture = owner.getLoadFuture();
            if (loadFuture != null && !loadFuture.isDone()) {
                owner.getInteractionGate().release(lease);
                return CompletableFuture.completedFuture(ClearResult.BUSY);
            }
            if (loadFuture != null && loadFuture.isCompletedExceptionally()) {
                final Throwable loadFailure = extractFailure(loadFuture);
                owner.setLoadFuture(null);
                owner.getInteractionGate().release(lease);
                if (owner.getData() == null) {
                    final CompletableFuture<ClearResult> failed = new CompletableFuture<>();
                    failed.completeExceptionally(loadFailure == null
                            ? new IllegalStateException("Failed to load chest for " + ownerUuid)
                            : loadFailure);
                    return failed;
                }
                return CompletableFuture.completedFuture(ClearResult.BUSY);
            }
            if (shuttingDown) {
                owner.getInteractionGate().release(lease);
                return CompletableFuture.completedFuture(ClearResult.SHUTTING_DOWN);
            }
            final ChestData memory = owner.getData();
            final int known = memory == null ? 0 : Math.max(memory.lastKnownRows(), memory.occupiedRows());
            minRows = clampRows(Math.max(config.getStorageDefaultRows(),
                    Math.max(owner.getVisibleRows(), known)));
            clearName = owner.getOwnerName();
            owner.setClearing(true);
        }
        final CompletableFuture<ClearResult> result = new CompletableFuture<>();
        final CompletableFuture<ChestData> clearFuture;
        try {
            clearFuture = persistence.clear(owner.getOwnerUuid(), clearName, minRows);
        } catch (Exception ex) {
            synchronized (owner) {
                owner.setClearing(false);
            }
            owner.getInteractionGate().release(lease);
            result.completeExceptionally(ex);
            return result;
        }
        if (clearFuture == null) {
            synchronized (owner) {
                owner.setClearing(false);
            }
            owner.getInteractionGate().release(lease);
            result.completeExceptionally(new IllegalStateException("Failed to clear chest for " + ownerUuid));
            return result;
        }
        clearFuture.whenComplete((savedBlank, failure) -> {
            if (failure != null || savedBlank == null) {
                synchronized (owner) {
                    owner.setClearing(false);
                }
                owner.getInteractionGate().release(lease);
                result.completeExceptionally(failure != null ? unwrapFailure(failure)
                        : new IllegalStateException("Failed to clear chest for " + ownerUuid));
                return;
            }
            synchronized (owner) {
                final int visible = clampRows(Math.max(owner.getVisibleRows(), savedBlank.lastKnownRows()));
                owner.setDataAndRows(savedBlank, visible, false);
                owner.getClassicPageInventories().clear();
                owner.incrementRevision();
                owner.setClearing(false);
            }
            owner.getInteractionGate().release(lease);
            result.complete(ClearResult.CLEARED);
        });
        return result;
    }

    public void handleInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ManagedEnderChestHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        final ManagedEnderChestHolder holder = (ManagedEnderChestHolder) event.getInventory().getHolder();
        final Player viewer = (Player) event.getWhoClicked();
        final OwnerChestSession owner = owners.get(holder.getOwnerUuid());
        if (owner == null) return;
        final ViewerChestSession view = owner.getViewers().get(viewer.getUniqueId());
        if (view == null) return;
        final long generation = view.getViewGeneration();
        final UUID token = view.getSessionToken();
        final boolean collect = actionName(event).equals("COLLECT_TO_CURSOR");
        if (holder.isControlSlot(event.getRawSlot()) || collect) {
            event.setCancelled(true);
            if (!collect) handleControlAction(viewer, owner, view, holder, event.getRawSlot());
            return;
        }
        final Optional<OwnerInteractionGate.Lease> lease = beginStorageMutation(owner, view);
        if (!lease.isPresent()) {
            event.setCancelled(true);
            return;
        }
        final OwnerInteractionGate.Lease taken = lease.get();
        final Inventory inventory = event.getInventory();
        folia.runEntityLater(viewer, new Runnable() {
            @Override public void run() {
                try {
                    finalizeStorageMutation(viewer, owner, view, holder, inventory, taken, token, generation);
                } finally {
                    owner.getInteractionGate().release(taken);
                }
            }
        }, 1L);
    }

    public void handleInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof ManagedEnderChestHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        final ManagedEnderChestHolder holder = (ManagedEnderChestHolder) event.getInventory().getHolder();
        final int start = holder.getControlRowStart();
        if (start >= 0) {
            final int topSize = event.getInventory().getSize();
            for (Integer slot : event.getRawSlots()) {
                if (slot != null && slot >= start && slot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        final Player viewer = (Player) event.getWhoClicked();
        final OwnerChestSession owner = owners.get(holder.getOwnerUuid());
        if (owner == null) return;
        final ViewerChestSession view = owner.getViewers().get(viewer.getUniqueId());
        if (view == null) return;
        final Optional<OwnerInteractionGate.Lease> lease = beginStorageMutation(owner, view);
        if (!lease.isPresent()) {
            event.setCancelled(true);
            return;
        }
        final OwnerInteractionGate.Lease taken = lease.get();
        final long generation = view.getViewGeneration();
        final UUID token = view.getSessionToken();
        final Inventory inventory = event.getInventory();
        folia.runEntityLater(viewer, new Runnable() {
            @Override public void run() {
                try {
                    finalizeStorageMutation(viewer, owner, view, holder, inventory, taken, token, generation);
                } finally {
                    owner.getInteractionGate().release(taken);
                }
            }
        }, 1L);
    }

    public void handleInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ManagedEnderChestHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        final ManagedEnderChestHolder holder = (ManagedEnderChestHolder) event.getInventory().getHolder();
        final OwnerChestSession owner = owners.get(holder.getOwnerUuid());
        if (owner == null) return;
        final Player viewer = (Player) event.getPlayer();
        final ViewerChestSession view = owner.getViewers().get(viewer.getUniqueId());
        if (view == null) return;
        boolean sameInventory = false;
        final Optional<Inventory> current = view.getCurrentInventory();
        if (current.isPresent() && current.get() == event.getInventory()) sameInventory = true;
        if (!sameInventory) return;
        if (owner.getInteractionGate().isBusy()) {
            view.markClosePending();
            return;
        }
        detachViewer(owner, view, viewer.getUniqueId());
        queueSave(owner.getOwnerUuid());
    }

    public void handleViewerQuit(final PlayerQuitEvent event) {
        final UUID viewerId = event.getPlayer().getUniqueId();
        final UUID ownerId = ownerByViewer.get(viewerId);
        if (ownerId == null) return;
        final OwnerChestSession owner = owners.get(ownerId);
        if (owner == null) {
            ownerByViewer.remove(viewerId, ownerId);
            return;
        }
        final ViewerChestSession view = owner.getViewers().get(viewerId);
        if (view != null) detachViewer(owner, view, viewerId);
        else ownerByViewer.remove(viewerId, ownerId);
        queueSave(ownerId);
    }

    public void scheduleAutoSavePass() {
        if (shuttingDown) return;
        for (OwnerChestSession owner : owners.values()) {
            synchronized (owner) {
                if (owner.getInteractionGate().isBusy()) continue;
                if (owner.isClearing()) continue;
                final ChestData snapshot = snapshotOwner(owner);
                if (snapshot == null) continue;
                try {
                    persistence.save(snapshot);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Autosave failed for " + owner.getOwnerUuid(), ex);
                }
            }
        }
    }

    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        final List<ChestData> snapshots = new ArrayList<>();
        for (OwnerChestSession owner : owners.values()) {
            synchronized (owner) {
                if (owner.isClearing()) continue;
                final ChestData snapshot = snapshotOwner(owner);
                if (snapshot != null) snapshots.add(snapshot);
            }
        }
        try {
            persistence.flushAndClose(snapshots);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to flush chest data on shutdown", ex);
        }
        owners.clear();
        ownerByViewer.clear();
    }

    public boolean isManagedInventory(final Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ManagedEnderChestHolder;
    }

    public Optional<UUID> getManagedOwner(final Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof ManagedEnderChestHolder) {
            return Optional.of(((ManagedEnderChestHolder) inventory.getHolder()).getOwnerUuid());
        }
        return Optional.empty();
    }

    public void save(final UUID ownerUuid) {
        if (ownerUuid == null) return;
        final OwnerChestSession owner = owners.get(ownerUuid);
        if (owner == null) return;
        queueSave(ownerUuid);
    }

    private OwnerChestSession getOrCreateOwnerSession(final UUID ownerUuid, final String ownerName, final int rows) {
        OwnerChestSession existing = owners.get(ownerUuid);
        if (existing != null) {
            if (ownerName != null) existing.setOwnerName(ownerName);
            return existing;
        }
        final OwnerChestSession created = new OwnerChestSession(ownerUuid,
                ownerName == null ? ownerUuid.toString() : ownerName, clampRows(rows));
        existing = owners.putIfAbsent(ownerUuid, created);
        return existing == null ? created : existing;
    }

    private void bindViewer(final OwnerChestSession owner, final ViewerChestSession view) {
        owner.getViewers().put(view.getViewerUuid(), view);
        ownerByViewer.put(view.getViewerUuid(), owner.getOwnerUuid());
    }

    private void unbindViewer(final OwnerChestSession owner, final UUID viewerUuid) {
        owner.getViewers().remove(viewerUuid);
        ownerByViewer.remove(viewerUuid, owner.getOwnerUuid());
    }

    private void reconcileVisibleRows(final OwnerChestSession owner, final int liveRows, final boolean recovery) {
        final int clamped = clampRows(liveRows);
        final ChestData data = owner.getData();
        final int occupied = data == null ? 0 : data.occupiedRows();
        final int lastKnown = data == null ? 0 : data.lastKnownRows();
        final int capacity = clampRows(Math.max(clamped, Math.max(lastKnown, occupied)));
        if (recovery) {
            owner.setVisibleRows(clamped);
            owner.setOfflineRecoveryLayout(true);
            return;
        }
        if (owner.isOfflineRecoveryLayout()) {
            owner.setVisibleRows(capacity);
            owner.setOfflineRecoveryLayout(false);
            owner.getClassicPageInventories().clear();
        } else {
            final int lastKnownRows = data == null ? clamped : Math.max(clamped, lastKnown);
            owner.setVisibleRows(Math.max(owner.getVisibleRows(), 0) == 0 ? capacity
                    : Math.max(capacity, Math.min(owner.getVisibleRows(), 54)));
            if (data != null && lastKnownRows != lastKnown) {
                final ItemStack[] contents = data.copyContents();
                final ItemStack[] grown = new ItemStack[Math.max(contents.length, lastKnownRows * 9)];
                for (int i = 0; i < contents.length; i++) grown[i] = contents[i];
                owner.setDataAndRows(new ChestData(owner.getOwnerUuid(), owner.getOwnerName(), lastKnownRows, grown),
                        Math.max(owner.getVisibleRows(), clamped), false);
            } else {
                owner.setVisibleRows(Math.max(owner.getVisibleRows(), clamped));
            }
        }
        if (owner.getVisibleRows() <= 0) owner.setVisibleRows(clamped);
    }

    private void loadOwnerOnce(final OwnerChestSession owner, final String ownerName) {
        if (owner.getData() != null) return;
        synchronized (owner) {
            if (owner.isClearing()) return;
            if (owner.getLoadFuture() != null) return;
            final CompletableFuture<ChestData> future;
            try {
                future = persistence.load(owner.getOwnerUuid(), ownerName == null ? owner.getOwnerName() : ownerName);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load chest for " + owner.getOwnerUuid(), ex);
                return;
            }
            owner.setLoadFuture(future);
            future.whenComplete((data, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load chest for " + owner.getOwnerUuid(), failure);
                    return;
                }
                if (data == null) return;
                final int capacity = clampRows(Math.max(data.lastKnownRows(),
                        Math.max(data.occupiedRows(), owner.getVisibleRows())));
                final ItemStack[] loaded = data.copyContents();
                final ItemStack[] bounded = new ItemStack[Math.max(loaded.length, capacity * 9)];
                for (int i = 0; i < loaded.length; i++) bounded[i] = loaded[i];
                owner.setDataAndRows(new ChestData(owner.getOwnerUuid(), data.ownerName(),
                        Math.max(data.lastKnownRows(), capacity), bounded), capacity, owner.isOfflineRecoveryLayout());
                owner.setLoading(false);
            });
        }
    }

    private OpenResult openViewerPage(final Player viewer, final OwnerChestSession owner, final int pageIndex) {
        final ViewerChestSession view;
        synchronized (owner) {
            if (owner.isClearing()) return OpenResult.BUSY;
            ViewerChestSession existing = owner.getViewers().get(viewer.getUniqueId());
            if (existing == null) {
                existing = new ViewerChestSession(viewer.getUniqueId());
                bindViewer(owner, existing);
            }
            view = existing;
        }
        final StorageLayout layout = StorageLayout.forRows(Math.max(1, owner.getVisibleRows()));
        final int clamped = Math.max(0, Math.min(pageIndex, layout.pageCount() - 1));
        final Inventory inventory;
        if (folia.isFolia()) {
            inventory = createFoliaMirror(owner, layout.page(clamped));
        } else {
            inventory = getOrCreateClassicPage(owner, layout.page(clamped));
        }
        view.switchView(clamped, inventory);
        final OwnerInteractionGate.Lease nav = owner.getInteractionGate().tryAcquire(
                viewer.getUniqueId(), view.getSessionToken()).orElse(null);
        try {
            viewer.openInventory(inventory);
        } finally {
            if (nav != null) owner.getInteractionGate().release(nav);
        }
        return OpenResult.STARTED;
    }

    private Inventory getOrCreateClassicPage(final OwnerChestSession owner, final StorageLayout.Page page) {
        Inventory cached = owner.getClassicPageInventories().get(page.pageIndex());
        if (cached != null) {
            renderControlRow(owner, cached, page);
            return cached;
        }
        final String title = titleFor(page);
        final ManagedEnderChestHolder holder = new ManagedEnderChestHolder(owner.getOwnerUuid(),
                owner.getOwnerName(), page.pageIndex(), page.pageCount(), page.storageOffset(),
                page.storageSlots(), page.controlRowStart());
        final Inventory created = Bukkit.createInventory(holder, page.inventoryRows() * 9, title);
        holder.bindInventory(created);
        renderPage(owner, created, holder, page);
        owner.getClassicPageInventories().put(page.pageIndex(), created);
        return created;
    }

    private Inventory createFoliaMirror(final OwnerChestSession owner, final StorageLayout.Page page) {
        final String title = titleFor(page);
        final ManagedEnderChestHolder holder = new ManagedEnderChestHolder(owner.getOwnerUuid(),
                owner.getOwnerName(), page.pageIndex(), page.pageCount(), page.storageOffset(),
                page.storageSlots(), page.controlRowStart());
        final Inventory mirror = Bukkit.createInventory(holder, page.inventoryRows() * 9, title);
        holder.bindInventory(mirror);
        renderPage(owner, mirror, holder, page);
        return mirror;
    }

    private void renderPage(final OwnerChestSession owner, final Inventory inventory,
                            final ManagedEnderChestHolder holder, final StorageLayout.Page page) {
        final ChestData data = owner.getData();
        final ItemStack[] contents = data == null ? new ItemStack[0] : data.copyContents();
        for (int i = 0; i < holder.getStorageCount(); i++) {
            final int global = holder.getStorageOffset() + i;
            if (global < contents.length && contents[global] != null) inventory.setItem(i, contents[global].clone());
            else inventory.setItem(i, null);
        }
        renderControlRow(owner, inventory, page);
    }

    private static ControlItemDefinition fillerFallback() {
        final String modern = "GRAY_" + "STAINED_" + "GLASS_" + "PANE";
        final String legacy = "STAINED_" + "GLASS_" + "PANE";
        return new ControlItemDefinition(modern, legacy, 7, " ",
                Collections.<String>emptyList());
    }

    private void renderControlRow(final OwnerChestSession owner, final Inventory inventory,
                                  final StorageLayout.Page page) {
        if (!page.paginated() || page.controlRowStart() < 0) return;
        final Map<String, String> infoPlaceholders = new HashMap<>();
        infoPlaceholders.put("page", String.valueOf(page.pageIndex() + 1));
        infoPlaceholders.put("pages", String.valueOf(page.pageCount()));
        final ControlItemDefinition fillerDef = config.getControlItem("filler", fillerFallback());
        for (int i = page.controlRowStart(); i < page.controlRowStart() + 9; i++) {
            inventory.setItem(i, createControlItem(fillerDef, Collections.<String, String>emptyMap()));
        }
        if (page.hasPrevious()) {
            final Map<String, String> prevPlaceholders = new HashMap<>();
            prevPlaceholders.put("page", String.valueOf(page.pageIndex()));
            prevPlaceholders.put("pages", String.valueOf(page.pageCount()));
            inventory.setItem(page.previousSlot(),
                    createControlItem(config.getControlItem("previous-page", "ARROW", "Previous page"), prevPlaceholders));
        }
        inventory.setItem(page.pageInfoSlot(),
                createControlItem(config.getControlItem("page-info", "PAPER", "Page {page}/{pages}"), infoPlaceholders));
        if (page.hasNext()) {
            final Map<String, String> nextPlaceholders = new HashMap<>();
            nextPlaceholders.put("page", String.valueOf(page.pageIndex() + 2));
            nextPlaceholders.put("pages", String.valueOf(page.pageCount()));
            inventory.setItem(page.nextSlot(),
                    createControlItem(config.getControlItem("next-page", "ARROW", "Next page"), nextPlaceholders));
        }
    }

    private ItemStack createControlItem(final ControlItemDefinition definition, final Map<String, String> placeholders) {
        final ItemStack item;
        try {
            item = Materials.create(definition.modernMaterialName(), definition.legacyMaterialName(), definition.legacyData());
        } catch (IllegalStateException ex) {
            plugin.getLogger().log(Level.SEVERE, "Unknown control material: " + definition.materialName(), ex);
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        final TextCodec codec = textCodec;
        if (codec != null) {
            meta.setDisplayName(codec.itemName(definition.name(), placeholders));
            meta.setLore(codec.itemLore(definition.lore(), placeholders));
        } else {
            String name = definition.name();
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    name = name.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            meta.setDisplayName(name);
            final List<String> lore = new ArrayList<>();
            if (definition.lore() != null) {
                for (String line : definition.lore()) {
                    String resolved = line;
                    if (placeholders != null) {
                        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
                        }
                    }
                    lore.add(resolved);
                }
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void handleControlAction(final Player viewer, final OwnerChestSession owner,
                                     final ViewerChestSession view, final ManagedEnderChestHolder holder, final int rawSlot) {
        final StorageLayout layout = StorageLayout.forRows(Math.max(1, owner.getVisibleRows()));
        final StorageLayout.Page page;
        try {
            page = layout.page(holder.getPageIndex());
        } catch (IllegalArgumentException ex) {
            return;
        }
        final boolean isPrev = page.hasPrevious() && rawSlot == page.previousSlot();
        final boolean isNext = page.hasNext() && rawSlot == page.nextSlot();
        if (!isPrev && !isNext) return;
        final Optional<OwnerInteractionGate.Lease> lease = beginStorageMutation(owner, view);
        if (!lease.isPresent()) return;
        final OwnerInteractionGate.Lease taken = lease.get();
        try {
            commitStoragePage(owner, view, holder);
            final int target = isPrev ? page.pageIndex() - 1 : page.pageIndex() + 1;
            final StorageLayout.Page targetPage = layout.page(Math.max(0, Math.min(target, layout.pageCount() - 1)));
            final Inventory inventory = folia.isFolia()
                    ? createFoliaMirror(owner, targetPage) : getOrCreateClassicPage(owner, targetPage);
            view.switchView(targetPage.pageIndex(), inventory);
            viewer.openInventory(inventory);
        } finally {
            owner.getInteractionGate().release(taken);
        }
    }

    private Optional<OwnerInteractionGate.Lease> beginStorageMutation(final OwnerChestSession owner,
                                                                     final ViewerChestSession view) {
        if (shuttingDown) return Optional.empty();
        return owner.getInteractionGate().tryAcquire(view.getViewerUuid(), view.getSessionToken());
    }

    private void finalizeStorageMutation(final Player viewer, final OwnerChestSession owner,
                                         final ViewerChestSession view, final ManagedEnderChestHolder holder,
                                         final Inventory inventory, final OwnerInteractionGate.Lease lease,
                                         final UUID token, final long generation) {
        if (shuttingDown) return;
        if (!owner.getInteractionGate().isCurrent(lease)) return;
        if (!token.equals(view.getSessionToken())) return;
        if (generation != view.getViewGeneration()) return;
        final Optional<Inventory> current = view.getCurrentInventory();
        if (!current.isPresent() || current.get() != inventory) return;
        if (!viewer.isOnline()) return;
        commitStoragePage(owner, view, holder);
        renderControlRow(owner, inventory, StorageLayout.forRows(Math.max(1, owner.getVisibleRows())).page(holder.getPageIndex()));
        refreshOtherViewers(owner, view.getViewerUuid());
        if (view.isClosePending()) {
            view.clearClosePending();
            detachViewer(owner, view, viewer.getUniqueId());
            queueSave(owner.getOwnerUuid());
        }
    }

    private void commitStoragePage(final OwnerChestSession owner, final ViewerChestSession view,
                                   final ManagedEnderChestHolder holder) {
        final Optional<Inventory> current = view.getCurrentInventory();
        if (!current.isPresent()) return;
        final Inventory inventory = current.get();
        final ChestData data = owner.getData();
        final int need = holder.getStorageOffset() + holder.getStorageCount();
        final ItemStack[] base = data == null ? new ItemStack[0] : data.copyContents();
        final ItemStack[] merged = new ItemStack[Math.max(base.length, need)];
        for (int i = 0; i < base.length; i++) merged[i] = base[i];
        for (int i = 0; i < holder.getStorageCount(); i++) {
            final ItemStack stack = inventory.getItem(i);
            merged[holder.getStorageOffset() + i] = stack == null ? null : stack.clone();
        }
        final int newLastKnown = Math.max(data == null ? 0 : data.lastKnownRows(),
                Math.max(owner.getVisibleRows(), occupiedRowsOf(merged)));
        final ItemStack[] sized = new ItemStack[Math.max(merged.length, newLastKnown * 9)];
        for (int i = 0; i < merged.length; i++) sized[i] = merged[i];
        owner.setDataAndRows(new ChestData(owner.getOwnerUuid(), owner.getOwnerName(), newLastKnown, sized),
                owner.getVisibleRows(), owner.isOfflineRecoveryLayout());
        owner.incrementRevision();
    }

    private void refreshOtherViewers(final OwnerChestSession owner, final UUID excludeViewer) {
        final ChestData data = owner.getData();
        if (data == null) return;
        for (Map.Entry<UUID, ViewerChestSession> entry : owner.getViewers().entrySet()) {
            if (entry.getKey().equals(excludeViewer)) continue;
            refreshViewerMirror(owner, entry.getValue());
        }
    }

    private void refreshViewerMirror(final OwnerChestSession owner, final ViewerChestSession view) {
        final Player player = Bukkit.getPlayer(view.getViewerUuid());
        if (player == null || !player.isOnline()) return;
        final Optional<Inventory> current = view.getCurrentInventory();
        if (!current.isPresent()) return;
        final Inventory inventory = current.get();
        if (!(inventory.getHolder() instanceof ManagedEnderChestHolder)) return;
        final ManagedEnderChestHolder holder = (ManagedEnderChestHolder) inventory.getHolder();
        final StorageLayout layout = StorageLayout.forRows(Math.max(1, owner.getVisibleRows()));
        final int clamped = Math.max(0, Math.min(holder.getPageIndex(), layout.pageCount() - 1));
        final UUID token = view.getSessionToken();
        final long generation = view.getViewGeneration();
        folia.runEntity(player, new Runnable() {
            @Override public void run() {
                if (!token.equals(view.getSessionToken())) return;
                if (generation != view.getViewGeneration()) return;
                final Optional<Inventory> now = view.getCurrentInventory();
                if (!now.isPresent() || now.get() != inventory) return;
                renderPage(owner, inventory, holder, layout.page(clamped));
            }
        });
    }

    private void queueSave(final UUID ownerUuid) {
        final OwnerChestSession owner = owners.get(ownerUuid);
        if (owner == null) return;
        synchronized (owner) {
            if (owner.isClearing()) return;
            final ChestData snapshot = snapshotOwner(owner);
            if (snapshot == null) return;
            try {
                persistence.save(snapshot);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save chest for " + ownerUuid, ex);
            }
        }
    }

    private ChestData snapshotOwner(final OwnerChestSession owner) {
        final ChestData data = owner.getData();
        if (data == null) return null;
        return new ChestData(data.ownerUuid(), data.ownerName(), data.lastKnownRows(), data.copyContents());
    }

    private static Throwable extractFailure(final CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException ex) {
            return ex.getCause() == null ? ex : ex.getCause();
        } catch (Exception ex) {
            return ex;
        }
    }

    private static Throwable unwrapFailure(final Throwable failure) {
        if (failure instanceof CompletionException && failure.getCause() != null) return failure.getCause();
        return failure;
    }

    private void detachViewer(final OwnerChestSession owner, final ViewerChestSession view, final UUID viewerUuid) {
        view.clearClosePending();
        unbindViewer(owner, viewerUuid);
    }

    private String handlePendingClose(final ViewerChestSession view) {
        return view.isClosePending() ? "pending" : "open";
    }

    private String titleFor(final StorageLayout.Page page) {
        final String template = config.getInventoryTitleTemplate();
        final TextCodec codec = textCodec;
        if (codec != null) return codec.inventoryTitle(template, page.pageIndex() + 1, page.pageCount());
        if (template == null || template.isEmpty()) {
            return "Better Ender Chest (Page " + (page.pageIndex() + 1) + "/" + page.pageCount() + ")";
        }
        return template.replace("{page}", String.valueOf(page.pageIndex() + 1))
                .replace("{pages}", String.valueOf(page.pageCount()));
    }

    private int clampRows(final int rows) {
        return Math.max(1, Math.min(54, rows));
    }

    private static int occupiedRowsOf(final ItemStack[] contents) {
        int highest = -1;
        for (int i = 0; i < contents.length; i++) if (contents[i] != null) highest = i;
        return highest < 0 ? 0 : (highest + 9) / 9;
    }

    private static String actionName(final InventoryClickEvent event) {
        try {
            final InventoryAction action = event.getAction();
            if (action == null) return "";
            return action.name();
        } catch (Exception ignored) {
            return "";
        }
    }
}
