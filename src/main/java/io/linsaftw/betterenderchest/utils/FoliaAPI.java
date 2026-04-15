package io.linsaftw.betterenderchest.utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Method;
import java.util.function.Consumer;

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

    public void runGlobalTimer(final Consumer<Object> task, final long initialDelayTicks, final long periodTicks) {
        if (this.folia) {
            invoke(this.globalRunAtFixedRate, this.globalRegionScheduler, this.plugin, task, normalizeInitialDelay(initialDelayTicks), periodTicks);
        } else {
            this.bukkitScheduler.runTaskTimer(this.plugin, () -> task.accept(null), initialDelayTicks, periodTicks);
        }
    }

    public void runEntity(final Entity entity, final Runnable task) {
        if (this.folia) {
            Object entityScheduler = invoke(this.entityGetScheduler, entity);
            invoke(this.entitySchedulerExecute, entityScheduler, this.plugin, task, null, 0L);
        } else {
            this.bukkitScheduler.runTask(this.plugin, task);
        }
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
