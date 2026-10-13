package io.linsaftw.betterenderchest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.storage.ChestData;
import io.linsaftw.betterenderchest.storage.ChestPersistenceService;
import io.linsaftw.betterenderchest.storage.ChestRepository;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class EnderChestServiceClearTest {

  private FakeRepository repository;
  private ChestPersistenceService persistence;
  private EnderChestService service;

  @BeforeEach
  public void setUp() throws Exception {
    repository = new FakeRepository();
    persistence = new ChestPersistenceService(repository, Logger.getLogger("clear-test"));
    final BetterEnderChestConfig config = new BetterEnderChestConfig(null);
    final YamlConfiguration backing = new YamlConfiguration();
    backing.set("storage.default-rows", 3);
    final Field configField = BetterEnderChestConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(config, backing);
    service = new EnderChestService(null, config, new PermissionService(config), persistence, null);
  }

  @AfterEach
  public void tearDown() {
    persistence.flushAndClose(Collections.<ChestData>emptyList());
  }

  @Test
  public void clearWithNoViewersEmptiesAndInvalidatesCache() throws Exception {
    final UUID ownerUuid = UUID.randomUUID();
    final ItemStack[] items = new ItemStack[27];
    items[0] = new ItemStack(Material.STONE);
    items[26] = new ItemStack(Material.STONE);
    final OwnerChestSession owner = new OwnerChestSession(ownerUuid, "Owner", 3);
    owner.setDataAndRows(new ChestData(ownerUuid, "Owner", 3, items), 3, false);
    owner.getClassicPageInventories().put(0, fakeInventory());
    final long revisionBefore = owner.getRevision();
    repository.seed(new ChestData(ownerUuid, "Owner", 5, new ItemStack[45]));
    sessions().put(ownerUuid, owner);

    final EnderChestService.ClearResult result = service.clearChest(ownerUuid, "Owner")
        .get(10L, TimeUnit.SECONDS);

    assertEquals(EnderChestService.ClearResult.CLEARED, result);
    assertEquals(1, repository.loads.get());
    assertEquals(1, repository.saves.get());
    assertEquals(0, owner.getData().occupiedRows());
    assertTrue(owner.getClassicPageInventories().isEmpty());
    assertEquals(revisionBefore + 1, owner.getRevision());
    assertFalse(owner.isClearing());
    assertFalse(owner.getInteractionGate().isBusy());
    assertSame(owner, sessions().get(ownerUuid));
  }

  @Test
  public void clearWithActiveViewerRefusesBusyBeforePersistence() throws Exception {
    final UUID ownerUuid = UUID.randomUUID();
    final OwnerChestSession owner = new OwnerChestSession(ownerUuid, "Owner", 3);
    owner.setDataAndRows(ChestData.empty(ownerUuid, "Owner", 3), 3, false);
    owner.getViewers().put(UUID.randomUUID(), new ViewerChestSession(UUID.randomUUID()));
    sessions().put(ownerUuid, owner);

    final EnderChestService.ClearResult result = service.clearChest(ownerUuid, "Owner")
        .get(10L, TimeUnit.SECONDS);

    assertEquals(EnderChestService.ClearResult.BUSY, result);
    assertEquals(0, repository.loads.get());
    assertEquals(0, repository.saves.get());
    assertFalse(owner.isClearing());
    assertFalse(owner.getInteractionGate().isBusy());
  }

  @Test
  public void clearWithInFlightLoadRefusesBusyBeforePersistence() throws Exception {
    final UUID ownerUuid = UUID.randomUUID();
    final OwnerChestSession owner = new OwnerChestSession(ownerUuid, "Owner", 3);
    owner.setLoadFuture(new CompletableFuture<ChestData>());
    sessions().put(ownerUuid, owner);

    final EnderChestService.ClearResult result = service.clearChest(ownerUuid, "Owner")
        .get(10L, TimeUnit.SECONDS);

    assertEquals(EnderChestService.ClearResult.BUSY, result);
    assertEquals(0, repository.loads.get());
    assertEquals(0, repository.saves.get());
    assertFalse(owner.isClearing());
    assertFalse(owner.getInteractionGate().isBusy());
  }

  @Test
  public void clearFailureRetainsCache() throws Exception {
    final UUID ownerUuid = UUID.randomUUID();
    final OwnerChestSession owner = new OwnerChestSession(ownerUuid, "Owner", 3);
    final ChestData before = new ChestData(ownerUuid, "Owner", 3, seededItems());
    owner.setDataAndRows(before, 3, false);
    sessions().put(ownerUuid, owner);
    repository.seed(new ChestData(ownerUuid, "Owner", 3, new ItemStack[27]));
    repository.failOnSave = true;

    try {
      service.clearChest(ownerUuid, "Owner").get(10L, TimeUnit.SECONDS);
      fail("expected clear failure");
    } catch (Exception expected) {
      assertTrue(expected.getCause() instanceof IllegalStateException
          || expected instanceof java.util.concurrent.ExecutionException);
    }

    assertSame(before, owner.getData());
    assertEquals(1, owner.getData().occupiedRows());
    assertFalse(owner.isClearing());
    assertFalse(owner.getInteractionGate().isBusy());
  }

  @SuppressWarnings("unchecked")
  private ConcurrentMap<UUID, OwnerChestSession> sessions() throws Exception {
    final Field field = EnderChestService.class.getDeclaredField("owners");
    field.setAccessible(true);
    return (ConcurrentMap<UUID, OwnerChestSession>) field.get(service);
  }

  private static ItemStack[] seededItems() {
    final ItemStack[] items = new ItemStack[27];
    items[5] = new ItemStack(Material.STONE);
    return items;
  }

  private static Inventory fakeInventory() {
    return (Inventory) Proxy.newProxyInstance(EnderChestServiceClearTest.class.getClassLoader(),
        new Class<?>[] { Inventory.class },
        (proxy, method, args) -> {
          if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
          if (method.getName().equals("equals")) return proxy == args[0];
          if (method.getName().equals("toString")) return "fake-inventory";
          return null;
        });
  }

  private static final class FakeRepository implements ChestRepository {
    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicInteger saves = new AtomicInteger();
    private final Map<UUID, ChestData> stored = new ConcurrentHashMap<>();
    private volatile boolean failOnSave;
    private final List<ChestData> savedBlanks = Collections.synchronizedList(new ArrayList<ChestData>());

    private void seed(final ChestData data) {
      stored.put(data.ownerUuid(), data);
    }

    @Override
    public ChestData load(final UUID ownerUuid, final String ownerName) {
      loads.incrementAndGet();
      final ChestData data = stored.get(ownerUuid);
      if (data != null) return data;
      return ChestData.empty(ownerUuid, ownerName, 3);
    }

    @Override
    public void save(final ChestData data) {
      saves.incrementAndGet();
      if (failOnSave) throw new IllegalStateException("boom");
      savedBlanks.add(data);
      stored.put(data.ownerUuid(), data);
    }

    @Override
    public boolean exists(final UUID ownerUuid) {
      return stored.containsKey(ownerUuid);
    }
  }
}
