package io.linsaftw.betterenderchest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class OwnerInteractionGateTest {

  @Test
  public void firstAcquireSucceedsSecondFailsWhileHeld() {
    final OwnerInteractionGate gate = new OwnerInteractionGate();
    final Optional<OwnerInteractionGate.Lease> first =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID());
    assertTrue(first.isPresent());
    assertTrue(gate.isBusy());
    assertTrue(gate.isCurrent(first.get()));

    final Optional<OwnerInteractionGate.Lease> second =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID());
    assertEquals(Optional.empty(), second);
    assertTrue(gate.isBusy());
  }

  @Test
  public void currentReleasesAndNextAcquires() {
    final OwnerInteractionGate gate = new OwnerInteractionGate();
    final OwnerInteractionGate.Lease lease =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID()).get();
    assertTrue(gate.release(lease));
    assertFalse(gate.isBusy());

    final Optional<OwnerInteractionGate.Lease> next =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID());
    assertTrue(next.isPresent());
    assertTrue(gate.isBusy());
  }

  @Test
  public void staleLeaseCannotClearReplacement() {
    final OwnerInteractionGate gate = new OwnerInteractionGate();
    final OwnerInteractionGate.Lease first =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID()).get();
    assertTrue(gate.release(first));
    final OwnerInteractionGate.Lease second =
        gate.tryAcquire(UUID.randomUUID(), UUID.randomUUID()).get();
    assertFalse(gate.release(first));
    assertTrue(gate.isBusy());
    assertTrue(gate.isCurrent(second));
    assertFalse(gate.isCurrent(first));
  }
}
