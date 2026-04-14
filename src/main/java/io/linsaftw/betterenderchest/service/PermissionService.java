package io.linsaftw.betterenderchest.service;

import org.bukkit.entity.Player;

public class PermissionService {
    private static final String USE_PERMISSION = "betterenderchest.use";
    private static final String ADMIN_PERMISSION = "betterenderchest.admin";

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
}