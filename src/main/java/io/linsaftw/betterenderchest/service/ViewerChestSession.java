package io.linsaftw.betterenderchest.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.inventory.Inventory;

public final class ViewerChestSession {

  private final UUID viewerUuid;
  private final UUID sessionToken;
  private volatile int pageIndex;
  private volatile Inventory currentInventory;
  private final AtomicLong viewGeneration = new AtomicLong(0L);
  private volatile boolean closePending;

  public ViewerChestSession(final UUID viewerUuid) {
    this.viewerUuid = viewerUuid;
    this.sessionToken = UUID.randomUUID();
  }

  public UUID getViewerUuid() {
    return viewerUuid;
  }

  public UUID getSessionToken() {
    return sessionToken;
  }

  public int getPageIndex() {
    return pageIndex;
  }

  public long getViewGeneration() {
    return viewGeneration.get();
  }

  public Optional<Inventory> getCurrentInventory() {
    return Optional.ofNullable(currentInventory);
  }

  public void switchView(final int page, final Inventory inv) {
    this.pageIndex = page;
    this.currentInventory = inv;
    this.viewGeneration.incrementAndGet();
  }

  public void markClosePending() {
    this.closePending = true;
  }

  public void clearClosePending() {
    this.closePending = false;
  }

  public boolean isClosePending() {
    return closePending;
  }
}
