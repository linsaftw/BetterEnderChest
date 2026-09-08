package io.linsaftw.betterenderchest;

import io.linsaftw.betterenderchest.command.BetterEnderChestMainCommand;
import io.linsaftw.betterenderchest.command.EnderChestAdminCommand;
import io.linsaftw.betterenderchest.command.EnderChestCommand;
import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.text.TextBridge;
import io.linsaftw.betterenderchest.text.TextCodec;
import io.linsaftw.betterenderchest.utils.FoliaAPI;
import io.linsaftw.betterenderchest.listener.EnderChestListener;
import io.linsaftw.betterenderchest.service.EnderChestService;
import io.linsaftw.betterenderchest.service.PermissionService;
import io.linsaftw.betterenderchest.storage.ChestPersistenceService;
import io.linsaftw.betterenderchest.storage.ChestRepository;
import io.linsaftw.betterenderchest.storage.YamlChestRepository;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterEnderChestPlugin extends JavaPlugin {
    private static BetterEnderChestPlugin instance;

    private BetterEnderChestConfig pluginConfig;
    private TextCodec textCodec;
    private TextBridge textBridge;
    private FoliaAPI foliaApi;
    private PermissionService permissionService;
    private ChestRepository chestRepository;
    private ChestPersistenceService persistenceService;
    private EnderChestService enderChestService;

    public void onEnable() {
        instance = this;
        this.pluginConfig = new BetterEnderChestConfig(this);
        this.pluginConfig.reload();
        this.textCodec = new TextCodec();
        this.textBridge = new TextBridge(this, this.textCodec);
        this.foliaApi = new FoliaAPI(this);
        this.getLogger().info(this.foliaApi.isFolia() ? "Folia mode: enabled" : "Folia mode: disabled (Bukkit/Paper scheduler fallback)");
        this.permissionService = new PermissionService(this.pluginConfig);
        this.chestRepository = new YamlChestRepository(this.getLogger(), this.getDataFolder().toPath().resolve("enderchests"));
        this.persistenceService = new ChestPersistenceService(this.chestRepository, this.getLogger());
        this.enderChestService = new EnderChestService(this, this.pluginConfig, this.permissionService,
                this.persistenceService, this.foliaApi);
        this.enderChestService.setTextCodec(this.textCodec);
        this.enderChestService.setTextBridge(this.textBridge);
        this.registerCommands();
        this.registerListeners();
        this.startAutoSaveTask();
        this.getLogger().info("BetterEnderChest enabled.");
        this.getLogger().info("Author: LinsaFTW");
    }

    public void onDisable() {
        if (this.enderChestService != null) {
            this.enderChestService.shutdown();
        }
        if (this.foliaApi != null) {
            this.foliaApi.cancelPluginTasks();
        }
        if (this.textBridge != null) {
            this.textBridge.close();
            this.textBridge = null;
        }
        this.textCodec = null;
        instance = null;
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
        if (this.getCommand("enderchest") != null) {
            this.getCommand("enderchest").setExecutor(ecCommand);
            this.getCommand("enderchest").setTabCompleter(ecCommand);
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

        final BetterEnderChestMainCommand mainCommand = new BetterEnderChestMainCommand(this);
        if (this.getCommand("betterenderchest") != null) {
            this.getCommand("betterenderchest").setExecutor(mainCommand);
            this.getCommand("betterenderchest").setTabCompleter(mainCommand);
        } else {
            this.getLogger().severe("Failed to register 'betterenderchest' command - command not found in plugin.yml");
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
        this.foliaApi.runGlobalTimer(ignored -> this.enderChestService.scheduleAutoSavePass(), periodTicks, periodTicks);
    }

    public void reloadConfigValues() {
        if (this.pluginConfig != null) {
            this.pluginConfig.reload();
        }
    }

    public static BetterEnderChestPlugin getInstance() {
        return instance;
    }

    public BetterEnderChestConfig getPluginConfig() {
        return this.pluginConfig;
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

    public TextBridge getTextBridge() {
        return this.textBridge;
    }
}
