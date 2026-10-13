package io.linsaftw.betterenderchest.service;

import io.linsaftw.betterenderchest.config.BetterEnderChestConfig;
import io.linsaftw.betterenderchest.config.StorageTier;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class PermissionService {
    private static final String USE_PERMISSION = "betterenderchest.use";
    private static final String ADMIN_PERMISSION = "betterenderchest.admin";
    private final BetterEnderChestConfig config;

    public PermissionService(final BetterEnderChestConfig config) {
        this.config = config;
    }

    public boolean canUse(Player player) {
        return player.hasPermission(USE_PERMISSION)
                || player.hasPermission("enderchest.use");
    }

    public boolean canAdmin(Player player) {
        return player.hasPermission(ADMIN_PERMISSION)
                || player.hasPermission("enderchest.admin")
                || player.hasPermission("enderchest.admin.lol")
                || player.isOp();
    }

    public boolean canAdmin(CommandSender sender) {
        if (sender instanceof Player) {
            return canAdmin((Player) sender);
        }
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        return sender.isOp()
                || sender.hasPermission(ADMIN_PERMISSION)
                || sender.hasPermission("enderchest.admin")
                || sender.hasPermission("enderchest.admin.lol");
    }

    public int getStorageRows(final Player player) {
        int rows = config.getStorageDefaultRows();
        for (StorageTier tier : config.getStorageTiers()) {
            if (player.hasPermission(tier.permission())) {
                rows = Math.max(rows, tier.rows());
            }
        }
        return rows;
    }
}
