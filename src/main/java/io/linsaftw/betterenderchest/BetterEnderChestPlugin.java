package io.linsaftw.betterenderchest;

import io.linsaftw.betterenderchest.command.EnderChestAdminCommand;
import io.linsaftw.betterenderchest.command.EnderChestCommand;
import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.folia.FoliaTasks;
import io.linsaftw.betterenderchest.listener.EnderChestListener;
import io.linsaftw.betterenderchest.service.EnderChestService;
import io.linsaftw.betterenderchest.service.PermissionService;
import io.linsaftw.betterenderchest.storage.ChestRepository;
import io.linsaftw.betterenderchest.storage.YamlChestRepository;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterEnderChestPlugin extends JavaPlugin {
    private static BetterEnderChestPlugin instance;

    private BetterEnderChestConfig pluginConfig;
    private FoliaTasks foliaTasks;
    private PermissionService permissionService;
    private ChestRepository chestRepository;
    private EnderChestService enderChestService;

    public void onEnable() {
        instance = this;
        this.pluginConfig = new BetterEnderChestConfig(this);
        this.pluginConfig.reload();
        this.foliaTasks = new FoliaTasks(this);
        this.permissionService = new PermissionService();
        this.chestRepository = new YamlChestRepository(this);
        this.enderChestService = new EnderChestService(this, this.pluginConfig, this.chestRepository, this.foliaTasks);
        this.registerCommands();
        this.registerListeners();
        this.startAutoSaveTask();
        this.getLogger().info("BetterEnderChest enabled.");
        this.getLogger().info("Author: LinsaFTW");
    }

    public void onDisable() {
        if (this.enderChestService != null) {
            this.enderChestService.saveAll();
        }
        this.getLogger().info("BetterEnderChest disabled.");
    }

    private void registerCommands() {
        final EnderChestCommand ecCommand = new EnderChestCommand(this);
        if (this.getCommand("ec") != null) {
            this.getCommand("ec").setExecutor(ecCommand);
            this.getCommand("ec").setTabCompleter(ecCommand);
        } else {
            this.getLogger().severe("Failed to register 'ec' command - command not found in plugin.yml");
        }

        final EnderChestAdminCommand adminCommand = new EnderChestAdminCommand(this);
        if (this.getCommand("ecadmin") != null) {
            this.getCommand("ecadmin").setExecutor(adminCommand);
            this.getCommand("ecadmin").setTabCompleter(adminCommand);
        } else {
            this.getLogger().severe("Failed to register 'ecadmin' command - command not found in plugin.yml");
        }
    }

    private void registerListeners() {
        this.getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
    }

    private void startAutoSaveTask() {
        if (!this.pluginConfig.isAutoSaveEnabled()) {
            return;
        }

        final long periodTicks = this.pluginConfig.getAutoSaveIntervalTicks();
        this.foliaTasks.runGlobalTimer(ignored -> this.enderChestService.saveAll(), periodTicks, periodTicks);
    }

    public static BetterEnderChestPlugin getInstance() {
        return instance;
    }

    public BetterEnderChestConfig getPluginConfig() {
        return this.pluginConfig;
    }

    public FoliaTasks getFoliaTasks() {
        return this.foliaTasks;
    }

    public PermissionService getPermissionService() {
        return this.permissionService;
    }

    public ChestRepository getChestRepository() {
        return this.chestRepository;
    }

    public EnderChestService getEnderChestService() {
        return this.enderChestService;
    }
}