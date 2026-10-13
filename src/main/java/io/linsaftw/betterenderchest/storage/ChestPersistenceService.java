package io.linsaftw.betterenderchest.storage;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChestPersistenceService {
    private static final long CLOSE_TIMEOUT_SECONDS = 30L;

    private final ChestRepository repository;
    private final Logger logger;
    private final ExecutorService executor;
    private final Map<UUID, CompletableFuture<Void>> tails = new HashMap<>();

    private boolean closing;
    private CompletableFuture<Void> closeResult;

    public ChestPersistenceService(final ChestRepository repository, final Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newFixedThreadPool(2);
    }

    public CompletableFuture<ChestData> load(final UUID ownerUuid, final String ownerName) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
        synchronized (this) {
            rejectIfClosing();
            final CompletableFuture<Void> previous = tail(ownerUuid);
            final CompletableFuture<ChestData> result = previous.handle((ignored, failure) -> null)
                    .thenApplyAsync(ignored -> repository.load(ownerUuid, ownerName), executor);
            track(ownerUuid, result);
            return logFailure(result, ownerUuid, "load");
        }
    }

    public CompletableFuture<Void> save(final ChestData data) {
        Objects.requireNonNull(data, "data");
        final UUID ownerUuid = data.ownerUuid();
        synchronized (this) {
            rejectIfClosing();
            final CompletableFuture<Void> previous = tail(ownerUuid);
            final CompletableFuture<Void> result = previous.handle((ignored, failure) -> null)
                    .thenRunAsync(() -> repository.save(data), executor);
            track(ownerUuid, result);
            return logFailure(result, ownerUuid, "save");
        }
    }

    public CompletableFuture<ChestData> clear(final UUID ownerUuid, final String ownerName,
                                                final int minimumRows) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
        final int effectiveMinimum = Math.max(0, minimumRows);
        synchronized (this) {
            rejectIfClosing();
            final CompletableFuture<Void> previous = tail(ownerUuid);
            final CompletableFuture<ChestData> result = previous.handle((ignored, failure) -> null)
                    .thenApplyAsync(ignored -> {
                        final ChestData disk = repository.load(ownerUuid, ownerName);
                        int rows = Math.max(effectiveMinimum, disk.lastKnownRows());
                        if (rows < 0) {
                            rows = 0;
                        }
                        final int slots = Math.max(disk.slotCount(), rows * 9);
                        final ItemStack[] blank = new ItemStack[slots];
                        final ChestData blankData = new ChestData(ownerUuid,
                                disk.ownerName() != null ? disk.ownerName() : ownerName, rows, blank);
                        repository.save(blankData);
                        return blankData;
                    }, executor);
            track(ownerUuid, result);
            return logFailure(result, ownerUuid, "clear");
        }
    }

    public CompletableFuture<Void> flushAndClose(final Collection<ChestData> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        final List<ChestData> snapshotCopy = new ArrayList<>(snapshots);
        for (final ChestData data : snapshotCopy) {
            Objects.requireNonNull(data, "snapshots contains null");
        }
        synchronized (this) {
            if (closeResult != null) {
                return closeResult;
            }
            closing = true;
            closeResult = new CompletableFuture<>();
            final List<FinalSave> finalSaves = new ArrayList<>();
            for (final ChestData snapshot : snapshotCopy) {
                final UUID ownerUuid = snapshot.ownerUuid();
                final CompletableFuture<Void> result = tail(ownerUuid).handle((ignored, failure) -> null)
                        .thenRunAsync(() -> repository.save(snapshot), executor);
                track(ownerUuid, result);
                finalSaves.add(new FinalSave(ownerUuid, logFailure(result, ownerUuid, "save")));
            }
            closeAsync(finalSaves, closeResult);
            return closeResult;
        }
    }

    private CompletableFuture<Void> tail(final UUID ownerUuid) {
        final CompletableFuture<Void> existing = tails.get(ownerUuid);
        return existing == null ? CompletableFuture.completedFuture(null) : existing;
    }

    private void track(final UUID ownerUuid, final CompletableFuture<?> operation) {
        tails.put(ownerUuid, operation.handle((ignored, failure) -> null));
    }

    private <T> CompletableFuture<T> logFailure(final CompletableFuture<T> operation,
                                                 final UUID ownerUuid, final String action) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Async chest " + action + " failed for " + ownerUuid,
                        unwrap(failure));
            }
        });
        return operation;
    }

    private void closeAsync(final List<FinalSave> finalSaves, final CompletableFuture<Void> result) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLOSE_TIMEOUT_SECONDS);
        final Set<UUID> failedOwners = new LinkedHashSet<>();
        try {
            final CompletableFuture<?>[] futures = new CompletableFuture<?>[finalSaves.size()];
            for (int i = 0; i < finalSaves.size(); i++) {
                futures[i] = finalSaves.get(i).future;
            }
            CompletableFuture.allOf(futures).get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            addIncomplete(finalSaves, failedOwners);
        } catch (java.util.concurrent.ExecutionException exception) {
            addFailed(finalSaves, failedOwners);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Interrupted while closing chest persistence", exception);
            addIncomplete(finalSaves, failedOwners);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(remainingNanos(deadline), TimeUnit.NANOSECONDS)) {
                    addIncomplete(finalSaves, failedOwners);
                    executor.shutdownNow();
                    executor.awaitTermination(Math.max(1L, remainingNanos(deadline)), TimeUnit.NANOSECONDS);
                    addIncomplete(finalSaves, failedOwners);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.log(Level.SEVERE, "Interrupted while stopping chest persistence", exception);
                addIncomplete(finalSaves, failedOwners);
                executor.shutdownNow();
            }
        }

        addFailed(finalSaves, failedOwners);
        if (failedOwners.isEmpty()) {
            result.complete(null);
        } else {
            result.completeExceptionally(new ChestStorageException(
                    "Failed to flush chest owners: " + failedOwners));
        }
    }

    private static long remainingNanos(final long deadline) {
        return Math.max(1L, deadline - System.nanoTime());
    }

    private static void addIncomplete(final List<FinalSave> saves, final Set<UUID> owners) {
        for (final FinalSave save : saves) {
            if (!save.future.isDone()) {
                owners.add(save.ownerUuid);
            }
        }
    }

    private static void addFailed(final List<FinalSave> saves, final Set<UUID> owners) {
        for (final FinalSave save : saves) {
            if (save.future.isCompletedExceptionally()) {
                owners.add(save.ownerUuid);
            }
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void rejectIfClosing() {
        if (closing) {
            throw new RejectedExecutionException("Chest persistence service is closing");
        }
    }

    private static final class FinalSave {
        private final UUID ownerUuid;
        private final CompletableFuture<Void> future;

        private FinalSave(final UUID ownerUuid, final CompletableFuture<Void> future) {
            this.ownerUuid = ownerUuid;
            this.future = future;
        }
    }
}
