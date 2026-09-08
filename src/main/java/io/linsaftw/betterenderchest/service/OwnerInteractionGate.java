package io.linsaftw.betterenderchest.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class OwnerInteractionGate {

  private final AtomicReference<Lease> current = new AtomicReference<Lease>();

  public record Lease(UUID leaseId, UUID viewerUuid, UUID viewToken) {
  }

  public Optional<Lease> tryAcquire(final UUID viewerUuid, final UUID viewToken) {
    final Lease candidate = new Lease(UUID.randomUUID(), viewerUuid, viewToken);
    if (current.compareAndSet(null, candidate)) {
      return Optional.of(candidate);
    }
    return Optional.empty();
  }

  public boolean isCurrent(final Lease lease) {
    if (lease == null) {
      return false;
    }
    return lease.equals(current.get());
  }

  public boolean release(final Lease lease) {
    if (lease == null) {
      return false;
    }
    return current.compareAndSet(lease, null);
  }

  public boolean isBusy() {
    return current.get() != null;
  }
}
