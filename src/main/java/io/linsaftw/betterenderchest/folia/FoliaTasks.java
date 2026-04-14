package io.linsaftw.betterenderchest.folia;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class FoliaTasks {

    private final JavaPlugin plugin;
    private final boolean folia;

    private final AtomicReference<Object> globalScheduler = new AtomicReference<>();
    private final AtomicReference<Object> asyncScheduler = new AtomicReference<>();

    private final Method getGlobalRegionScheduler;
    private final Method getRegionScheduler;
    private final Method getAsyncScheduler;
    private final Method entityRun;
    private final Method entityRunLater;
    private final Method locationRun;

    public FoliaTasks(final JavaPlugin plugin) {
        this.plugin = plugin;
        boolean foliaDetected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException e) {
            foliaDetected = false;
        }
        this.folia = foliaDetected;

        if (foliaDetected) {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getGlobalRegionScheduler = findMethod(lookup, "getGlobalRegionScheduler", Object.class);
            getRegionScheduler = findMethod(lookup, "getRegionScheduler", Object.class);
            getAsyncScheduler = findMethod(lookup, "getAsyncScheduler", Object.class);
            entityRun = findMethod(lookup, "run", Void.class, Object.class, Runnable.class);
            entityRunLater = findMethod(lookup, "runDelayed", Void.class, Object.class, Runnable.class, Runnable.class);
            locationRun = findMethod(lookup, "run", Void.class, Object.class, Runnable.class);
        } else {
            getGlobalRegionScheduler = null;
            getRegionScheduler = null;
            getAsyncScheduler = null;
            entityRun = null;
            entityRunLater = null;
            locationRun = null;
        }
    }

    private static Method findMethod(MethodHandles.Lookup lookup, String name, Class<?> returnType, Class<?>... paramTypes) {
        try {
            Method m = lookup.lookupClass().getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Failed to find method: " + name);
        }
    }

    public boolean isFolia() {
        return this.folia;
    }

    public void runGlobal(final Runnable task) {
        if (this.folia) {
            runGlobalFolia(task);
        } else {
            runGlobalBukkit(task);
        }
    }

    public void runGlobalLater(final Runnable task, final long delayTicks) {
        if (this.folia) {
            runGlobalLaterFolia(task, delayTicks);
        } else {
            runGlobalLaterBukkit(task, delayTicks);
        }
    }

    public void runGlobalTimer(final Consumer<Object> task, final long delayTicks, final long periodTicks) {
        if (this.folia) {
            runGlobalTimerFolia(task, delayTicks, periodTicks);
        } else {
            runGlobalTimerBukkit(task, delayTicks, periodTicks);
        }
    }

    public void runEntity(final Entity entity, final Runnable task) {
        if (this.folia) {
            runEntityFolia(entity, task);
        } else {
            runEntityBukkit(entity, task);
        }
    }

    public void runEntityLater(final Entity entity, final Runnable task, final Runnable retiredTask, final long delayTicks) {
        if (this.folia) {
            runEntityLaterFolia(entity, task, retiredTask, delayTicks);
        } else {
            runEntityLaterBukkit(entity, task, retiredTask, delayTicks);
        }
    }

    public void runLocation(final Location location, final Runnable task) {
        if (this.folia) {
            runLocationFolia(location, task);
        } else {
            runLocationBukkit(location, task);
        }
    }

    public void runAsync(final Runnable task) {
        if (this.folia) {
            runAsyncFolia(task);
        } else {
            runAsyncBukkit(task);
        }
    }

    public void cancelAll() {
        if (this.folia) {
            throw new IllegalStateException("cancelAll not implemented for Folia");
        }
    }

    private void runGlobalFolia(Runnable task) {
        try {
            Object scheduler = this.globalScheduler.updateAndGet(current -> {
                if (current != null) return current;
                try {
                    Object server = getServer();
                    Method m = getGlobalRegionScheduler;
                    return m.invoke(server);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get global scheduler", e);
                }
            });
            Method m = scheduler.getClass().getDeclaredMethod("run", JavaPlugin.class, Runnable.class);
            m.setAccessible(true);
            m.invoke(scheduler, this.plugin, task);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run global task on Folia", e);
        }
    }

    private void runGlobalBukkit(Runnable task) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, task);
    }

    private void runGlobalLaterFolia(Runnable task, long delayTicks) {
        try {
            Object scheduler = this.globalScheduler.updateAndGet(current -> {
                if (current != null) return current;
                try {
                    Object server = getServer();
                    Method m = getGlobalRegionScheduler;
                    return m.invoke(server);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get global scheduler", e);
                }
            });
            Method m = scheduler.getClass().getDeclaredMethod("runDelayed", JavaPlugin.class, Runnable.class, Long.class);
            m.setAccessible(true);
            m.invoke(scheduler, this.plugin, task, delayTicks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run global later task on Folia", e);
        }
    }

    private void runGlobalLaterBukkit(Runnable task, long delayTicks) {
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, task, delayTicks);
    }

    private void runGlobalTimerFolia(Consumer<Object> task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = this.globalScheduler.updateAndGet(current -> {
                if (current != null) return current;
                try {
                    Object server = getServer();
                    Method m = getGlobalRegionScheduler;
                    return m.invoke(server);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get global scheduler", e);
                }
            });
            Method m = scheduler.getClass().getDeclaredMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, Long.class, Long.class);
            m.setAccessible(true);
            m.invoke(scheduler, this.plugin, task, delayTicks, periodTicks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run global timer on Folia", e);
        }
    }

    private void runGlobalTimerBukkit(Consumer<Object> task, long delayTicks, long periodTicks) {
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task::accept, delayTicks, periodTicks);
    }

    private void runEntityFolia(Entity entity, Runnable task) {
        try {
            Method m = entityRun;
            m.invoke(entity, this.plugin, task);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run entity task on Folia", e);
        }
    }

    private void runEntityBukkit(Entity entity, Runnable task) {
        entity.getServer().getScheduler().runTask(this.plugin, task);
    }

    private void runEntityLaterFolia(Entity entity, Runnable task, Runnable retiredTask, long delayTicks) {
        try {
            Method m = entityRunLater;
            m.invoke(entity, this.plugin, task, retiredTask, delayTicks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run entity later task on Folia", e);
        }
    }

    private void runEntityLaterBukkit(Entity entity, Runnable task, Runnable retiredTask, long delayTicks) {
        entity.getServer().getScheduler().runTaskLater(this.plugin, task, delayTicks);
    }

    private void runLocationFolia(Location location, Runnable task) {
        try {
            Object scheduler;
            try {
                Object server = getServer();
                Method m = getRegionScheduler;
                scheduler = m.invoke(server);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to get region scheduler", e);
            }
            Method m = locationRun;
            m.invoke(scheduler, this.plugin, location, task);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run location task on Folia", e);
        }
    }

    private void runLocationBukkit(Location location, Runnable task) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, task);
    }

    private void runAsyncFolia(Runnable task) {
        try {
            Object scheduler = this.asyncScheduler.updateAndGet(current -> {
                if (current != null) return current;
                try {
                    Object server = getServer();
                    Method m = getAsyncScheduler;
                    return m.invoke(server);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get async scheduler", e);
                }
            });
            Method m = scheduler.getClass().getDeclaredMethod("runNow", JavaPlugin.class, Consumer.class);
            m.setAccessible(true);
            m.invoke(scheduler, this.plugin, (Consumer<Runnable>) r -> r.run());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run async task on Folia", e);
        }
    }

    private void runAsyncBukkit(Runnable task) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, task);
    }

    private Object getServer() {
        try {
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".CraftServer");
            return craftServerClass.cast(this.plugin.getServer());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get server", e);
        }
    }

    private String getServerVersion() {
        String name = this.plugin.getServer().getClass().getPackage().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
