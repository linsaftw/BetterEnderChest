package io.linsaftw.betterenderchest.utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FoliaAPI {

    private final Plugin plugin;
    private final BukkitScheduler bukkitScheduler;
    private final boolean folia;

    private final Object globalRegionScheduler;
    private final Object asyncScheduler;
    private final Method entityGetScheduler;
    private final Method entitySchedulerExecute;
    private final Method globalRunAtFixedRate;
    private final Method globalCancelTasks;
    private final Method asyncRunNow;
    private final Method asyncCancelTasks;
    private final Method globalExecute;

    public FoliaAPI(final Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        this.plugin = plugin;
        this.bukkitScheduler = Bukkit.getScheduler();

        boolean detectedFolia = false;
        Object globalScheduler = null;
        Object asyncSched = null;
        Method entityGetSched = null;
        Method entitySchedExec = null;
        Method globalRunAtFixed = null;
        Method globalCancel = null;
        Method asyncRun = null;
        Method asyncCancel = null;
        Method globalExec = null;

        if (detectFolia()) {
            try {
                Class<?> regionizedServerClass = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");

                Method getGlobalRegionScheduler = requireMethod(Server.class, "getGlobalRegionScheduler");
                globalScheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

                Method getAsyncScheduler = requireMethod(Server.class, "getAsyncScheduler");
                asyncSched = getAsyncScheduler.invoke(Bukkit.getServer());

                entityGetSched = requireMethod(Entity.class, "getScheduler");

                Class<?> globalRegionSchedulerClass = globalScheduler.getClass();
                globalRunAtFixed = requireMethod(globalRegionSchedulerClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                globalExec = requireMethod(globalRegionSchedulerClass, "execute", Plugin.class, Runnable.class);
                globalCancel = requireMethod(globalRegionSchedulerClass, "cancelTasks", Plugin.class);

                Class<?> asyncSchedulerClass = asyncSched.getClass();
                asyncRun = requireMethod(asyncSchedulerClass, "runNow", Plugin.class, Consumer.class);
                asyncCancel = requireMethod(asyncSchedulerClass, "cancelTasks", Plugin.class);

                Class<?> entitySchedulerClass = entityGetSched.getReturnType();
                entitySchedExec = requireMethod(entitySchedulerClass, "execute", Plugin.class, Runnable.class, Runnable.class, long.class);

                detectedFolia = true;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia runtime detected but required scheduler anchor is missing", e);
            }
        }

        this.folia = detectedFolia;
        this.globalRegionScheduler = globalScheduler;
        this.asyncScheduler = asyncSched;
        this.entityGetScheduler = entityGetSched;
        this.entitySchedulerExecute = entitySchedExec;
        this.globalRunAtFixedRate = globalRunAtFixed;
        this.globalCancelTasks = globalCancel;
        this.asyncRunNow = asyncRun;
        this.asyncCancelTasks = asyncCancel;
        this.globalExecute = globalExec;
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Method requireMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Reflection invoke failed", e);
        }
    }

    private long normalizeInitialDelay(long delay) {
        return delay <= 0L ? 1L : delay;
    }

    public boolean isFolia() {
        return this.folia;
    }

    public void runAsync(final Runnable task) {
        if (this.folia) {
            invoke(this.asyncRunNow, this.asyncScheduler, this.plugin, (Consumer<?>) ignored -> task.run());
        } else {
            this.bukkitScheduler.runTaskAsynchronously(this.plugin, task);
        }
    }

    public void runGlobal(final Runnable task) {
        if (this.folia) {
            invoke(this.globalExecute, this.globalRegionScheduler, this.plugin, task);
        } else {
            this.bukkitScheduler.runTask(this.plugin, task);
        }
    }

    public void runGlobalTimer(final Consumer<Object> task, final long initialDelayTicks, final long periodTicks) {
        if (this.folia) {
            invoke(this.globalRunAtFixedRate, this.globalRegionScheduler, this.plugin, task, normalizeInitialDelay(initialDelayTicks), periodTicks);
        } else {
            this.bukkitScheduler.runTaskTimer(this.plugin, () -> task.accept(null), initialDelayTicks, periodTicks);
        }
    }

    public CompletableFuture<Boolean> runEntity(final Entity entity, final Runnable task) {
        final CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        final Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                    future.complete(Boolean.TRUE);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }
        };
        if (this.folia) {
            try {
                final Runnable retired = new Runnable() {
                    @Override
                    public void run() {
                        future.complete(Boolean.FALSE);
                    }
                };
                Object entityScheduler = invoke(this.entityGetScheduler, entity);
                Object accepted = invoke(this.entitySchedulerExecute, entityScheduler, this.plugin, wrapped, retired, 0L);
                if (Boolean.FALSE.equals(accepted)) {
                    future.complete(Boolean.FALSE);
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            try {
                this.bukkitScheduler.runTask(this.plugin, wrapped);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public CompletableFuture<Boolean> runEntityLater(final Entity entity, final Runnable task, final long delayTicks) {
        final CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        final Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                    future.complete(Boolean.TRUE);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }
        };
        if (this.folia) {
            try {
                final Runnable retired = new Runnable() {
                    @Override
                    public void run() {
                        future.complete(Boolean.FALSE);
                    }
                };
                Object entityScheduler = invoke(this.entityGetScheduler, entity);
                Object accepted = invoke(this.entitySchedulerExecute, entityScheduler, this.plugin, wrapped, retired, delayTicks);
                if (Boolean.FALSE.equals(accepted)) {
                    future.complete(Boolean.FALSE);
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            try {
                this.bukkitScheduler.runTaskLater(this.plugin, wrapped, delayTicks);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public <T> CompletableFuture<T> callEntity(final Entity entity, final Supplier<T> task) {
        final CompletableFuture<T> future = new CompletableFuture<T>();
        final Runnable wrapped = new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(task.get());
                } catch (CancellationException e) {
                    future.completeExceptionally(e);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }
        };
        if (this.folia) {
            try {
                final Runnable retired = new Runnable() {
                    @Override
                    public void run() {
                        future.completeExceptionally(new CancellationException("entity retired"));
                    }
                };
                Object entityScheduler = invoke(this.entityGetScheduler, entity);
                Object accepted = invoke(this.entitySchedulerExecute, entityScheduler, this.plugin, wrapped, retired, 0L);
                if (Boolean.FALSE.equals(accepted)) {
                    future.completeExceptionally(new CancellationException("entity retired"));
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            try {
                this.bukkitScheduler.runTask(this.plugin, wrapped);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public void cancelPluginTasks() {
        if (this.folia) {
            invoke(this.globalCancelTasks, this.globalRegionScheduler, this.plugin);
            invoke(this.asyncCancelTasks, this.asyncScheduler, this.plugin);
        } else {
            this.bukkitScheduler.cancelTasks(this.plugin);
        }
    }
}
