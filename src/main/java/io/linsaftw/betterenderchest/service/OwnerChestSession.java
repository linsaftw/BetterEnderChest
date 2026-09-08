package io.linsaftw.betterenderchest.service;

import io.linsaftw.betterenderchest.storage.ChestData;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OwnerChestSession {

  private final UUID ownerUuid;
  private volatile String ownerName;

  // Null until async load completes.
  private volatile ChestData data;
  private volatile int visibleRows;
  private final AtomicLong revision = new AtomicLong(0L);

  private volatile CompletableFuture<ChestData> loadFuture;
  private volatile boolean loading;

  private final ConcurrentHashMap<UUID, ViewerChestSession> viewers =
      new ConcurrentHashMap<UUID, ViewerChestSession>();
  private final ConcurrentHashMap<Integer, Inventory> classicPageInventories =
      new ConcurrentHashMap<Integer, Inventory>();

  private final OwnerInteractionGate interactionGate = new OwnerInteractionGate();

  private volatile boolean offlineRecoveryLayout;
  private volatile boolean closing;

  private final Object lock = new Object();

  public OwnerChestSession(final UUID ownerUuid, final String ownerName, final int visibleRows) {
    if (ownerUuid == null) {
      throw new IllegalArgumentException("ownerUuid must not be null");
    }
    if (ownerName == null) {
      throw new IllegalArgumentException("ownerName must not be null");
    }
    this.ownerUuid = ownerUuid;
    this.ownerName = ownerName;
    this.visibleRows = visibleRows;
  }

  public UUID getOwnerUuid() {
    return ownerUuid;
  }

  public String getOwnerName() {
    return ownerName;
  }

  public void setOwnerName(final String ownerName) {
    if (ownerName == null) {
      throw new IllegalArgumentException("ownerName must not be null");
    }
    synchronized (lock) {
      this.ownerName = ownerName;
    }
  }

  public ChestData getData() {
    return data;
  }

  public void setDataAndRows(final ChestData data, final int rows, final boolean recovery) {
    synchronized (lock) {
      this.data = data;
      this.visibleRows = rows;
      this.offlineRecoveryLayout = recovery;
    }
  }

  public int getVisibleRows() {
    return visibleRows;
  }

  public void setVisibleRows(final int visibleRows) {
    synchronized (lock) {
      this.visibleRows = visibleRows;
    }
  }

  public long getRevision() {
    return revision.get();
  }

  public long incrementRevision() {
    return revision.incrementAndGet();
  }

  public CompletableFuture<ChestData> getLoadFuture() {
    return loadFuture;
  }

  public void setLoadFuture(final CompletableFuture<ChestData> loadFuture) {
    synchronized (lock) {
      this.loadFuture = loadFuture;
      this.loading = loadFuture != null && !loadFuture.isDone();
    }
  }

  public boolean isLoading() {
    return loading;
  }

  public void setLoading(final boolean loading) {
    this.loading = loading;
  }

  public ConcurrentHashMap<UUID, ViewerChestSession> getViewers() {
    return viewers;
  }

  public ConcurrentHashMap<Integer, Inventory> getClassicPageInventories() {
    return classicPageInventories;
  }

  public OwnerInteractionGate getInteractionGate() {
    return interactionGate;
  }

  public boolean isOfflineRecoveryLayout() {
    return offlineRecoveryLayout;
  }

  public void setOfflineRecoveryLayout(final boolean offlineRecoveryLayout) {
    synchronized (lock) {
      this.offlineRecoveryLayout = offlineRecoveryLayout;
    }
  }

  public boolean isClosing() {
    return closing;
  }

  public void setClosing(final boolean closing) {
    this.closing = closing;
  }
}
