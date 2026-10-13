package io.linsaftw.betterenderchest.storage;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestPersistenceServiceTest {
    private static final Logger LOGGER = Logger.getLogger(ChestPersistenceServiceTest.class.getName());

    @Test
    void sameOwnerSavesRunInSubmissionOrder() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.blockFirstSave = true;
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final UUID owner = UUID.randomUUID();

        final CompletableFuture<Void> first = service.save(data(owner, "first"));
        assertTrue(repository.firstSaveStarted.await(5, TimeUnit.SECONDS));
        final CompletableFuture<Void> second = service.save(data(owner, "second"));
        assertFalse(second.isDone());
        repository.releaseFirstSave.countDown();

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertEquals(java.util.Arrays.asList("first", "second"), repository.savedNames);
        close(service);
    }

    @Test
    void failedOperationDoesNotBreakSameOwnerChain() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.failFirstSave = true;
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final UUID owner = UUID.randomUUID();

        final CompletableFuture<Void> first = service.save(data(owner, "failed"));
        assertThrows(CompletionException.class, () -> first.join());
        final CompletableFuture<Void> second = service.save(data(owner, "recovered"));

        assertDoesNotThrow(() -> second.get(5, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList("failed", "recovered"), repository.savedNames);
        close(service);
    }

    @Test
    void differentOwnersMakeProgressIndependently() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.blockFirstSave = true;
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final UUID firstOwner = UUID.randomUUID();
        final UUID secondOwner = UUID.randomUUID();

        final CompletableFuture<Void> first = service.save(data(firstOwner, "first"));
        assertTrue(repository.firstSaveStarted.await(5, TimeUnit.SECONDS));
        final CompletableFuture<Void> second = service.save(data(secondOwner, "second"));

        assertDoesNotThrow(() -> second.get(5, TimeUnit.SECONDS));
        assertFalse(first.isDone());
        repository.releaseFirstSave.countDown();
        assertDoesNotThrow(() -> first.get(5, TimeUnit.SECONDS));
        close(service);
    }

    @Test
    void clearWaitsForPriorSaveAndRemovesAllItems() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.blockFirstSave = true;
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final UUID owner = UUID.randomUUID();

        final CompletableFuture<Void> first = service.save(data(owner, "first"));
        assertTrue(repository.firstSaveStarted.await(5, TimeUnit.SECONDS));
        final CompletableFuture<ChestData> cleared = service.clear(owner, "Player", 1);
        assertFalse(cleared.isDone());
        repository.releaseFirstSave.countDown();

        first.get(5, TimeUnit.SECONDS);
        final ChestData result = cleared.get(5, TimeUnit.SECONDS);
        assertEquals(1, result.lastKnownRows());
        for (final ItemStack item : result.copyContents()) {
            assertNull(item);
        }
        for (final ItemStack item : repository.load(owner, "Player").copyContents()) {
            assertNull(item);
        }
        close(service);
    }

    @Test
    void clearOnDifferentOwnerIsNotBlocked() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.blockFirstSave = true;
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final UUID firstOwner = UUID.randomUUID();
        final UUID secondOwner = UUID.randomUUID();
        repository.stored.put(secondOwner, data(secondOwner, "seed"));

        final CompletableFuture<Void> first = service.save(data(firstOwner, "first"));
        assertTrue(repository.firstSaveStarted.await(5, TimeUnit.SECONDS));
        final CompletableFuture<ChestData> cleared = service.clear(secondOwner, "Second", 1);

        final ChestData result = assertDoesNotThrow(() -> cleared.get(5, TimeUnit.SECONDS));
        assertFalse(first.isDone());
        for (final ItemStack item : result.copyContents()) {
            assertNull(item);
        }
        repository.releaseFirstSave.countDown();
        assertDoesNotThrow(() -> first.get(5, TimeUnit.SECONDS));
        close(service);
    }

    @Test
    void clearSaveFailureCompletesExceptionallyWithoutBlanking() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.failAllSaves = true;
        final UUID owner = UUID.randomUUID();
        repository.stored.put(owner, data(owner, "seed"));
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);

        final CompletableFuture<ChestData> cleared = service.clear(owner, "Player", 1);

        final ExecutionException failure =
                assertThrows(ExecutionException.class, () -> cleared.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof ChestStorageException
                || failure.getCause().getCause() instanceof ChestStorageException);
        assertNotNull(repository.load(owner, "Player").copyContents()[0]);
        assertDoesNotThrow(() -> service.load(owner, "Player").get(5, TimeUnit.SECONDS));
        close(service);
    }

    @Test
    void clearLoadFailureCompletesExceptionallyWithoutSaving() throws Exception {
        final FakeRepository repository = new FakeRepository();
        repository.failLoad = true;
        final UUID owner = UUID.randomUUID();
        repository.stored.put(owner, data(owner, "seed"));
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);
        final int savesBefore = repository.saveCount;

        final CompletableFuture<ChestData> cleared = service.clear(owner, "Player", 1);

        assertThrows(CompletionException.class, cleared::join);
        assertEquals(savesBefore, repository.saveCount);
        assertTrue(repository.savedNames.isEmpty());
        assertNotNull(repository.stored.get(owner).copyContents()[0]);
        close(service);
    }

    @Test
    void clearRejectsNullOwnerAndClampsNegativeMinimumRows() throws Exception {
        final FakeRepository repository = new FakeRepository();
        final UUID owner = UUID.randomUUID();
        final ItemStack[] contents = new ItemStack[18];
        contents[0] = new ItemStack(Material.PAPER);
        repository.stored.put(owner, new ChestData(owner, "Player", 2, contents));
        final ChestPersistenceService service = new ChestPersistenceService(repository, LOGGER);

        assertThrows(NullPointerException.class, () -> service.clear(null, "Player", 1));
        final ChestData result = service.clear(owner, "Player", -5).get(5, TimeUnit.SECONDS);
        assertEquals(2, result.lastKnownRows());
        for (final ItemStack item : result.copyContents()) {
            assertNull(item);
        }
        close(service);
    }

    private static ChestData data(final UUID owner, final String name) {
        final ItemStack[] contents = new ItemStack[9];
        contents[0] = new ItemStack(Material.PAPER);
        return new ChestData(owner, name, 1, contents);
    }

    private static void close(final ChestPersistenceService service) {
        service.flushAndClose(Collections.emptyList()).join();
    }

    private static final class FakeRepository implements ChestRepository {
        private final List<String> savedNames = Collections.synchronizedList(new ArrayList<>());
        private final Map<UUID, ChestData> stored = new ConcurrentHashMap<>();
        private final CountDownLatch firstSaveStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSave = new CountDownLatch(1);
        private boolean blockFirstSave;
        private boolean failFirstSave;
        private boolean failAllSaves;
        private boolean failLoad;
        private int saveCount;

        @Override
        public ChestData load(final UUID ownerUuid, final String ownerName) {
            if (failLoad) {
                throw new ChestStorageException("expected load failure");
            }
            final ChestData existing = stored.get(ownerUuid);
            if (existing != null) {
                return new ChestData(existing.ownerUuid(), existing.ownerName(),
                        existing.lastKnownRows(), existing.copyContents());
            }
            return ChestData.empty(ownerUuid, ownerName, 1);
        }

        @Override
        public void save(final ChestData data) {
            final int currentSave;
            synchronized (this) {
                currentSave = ++saveCount;
                savedNames.add(data.ownerName());
            }
            if (currentSave == 1 && blockFirstSave) {
                firstSaveStarted.countDown();
                await(releaseFirstSave);
            }
            if (failAllSaves || (currentSave == 1 && failFirstSave)) {
                throw new ChestStorageException("expected failure");
            }
            stored.put(data.ownerUuid(), new ChestData(data.ownerUuid(), data.ownerName(),
                    data.lastKnownRows(), data.copyContents()));
        }

        @Override
        public boolean exists(final UUID ownerUuid) {
            return stored.containsKey(ownerUuid);
        }

        private static void await(final CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for test release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }
}
