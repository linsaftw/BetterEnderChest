package io.linsaftw.betterenderchest.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EnderChestCommand implements CommandExecutor, TabCompleter {
    private final BetterEnderChestPlugin plugin;

    public EnderChestCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getPluginConfig().getMessage("player-only"));
            return true;
        } else {
            Player player = (Player) sender;
            if (!this.plugin.getPermissionService().canUse(player)) {
                player.sendMessage(this.plugin.getPluginConfig().getMessage("no-permission"));
                return true;
            }

            try {
                boolean opened = this.plugin.getEnderChestService().openOwnChest(player);
                if (!opened) {
                    player.sendMessage(this.plugin.getPluginConfig().getMessage("chest-in-use"));
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(this.plugin.getPluginConfig().getMessage("error-loading"));
                this.plugin.getLogger().severe("Error opening enderchest for " + player.getName() + ": " + e.getMessage());
                this.plugin.getLogger().severe("Stack trace: ");
                this.plugin.getLogger().severe(Arrays.toString(e.getStackTrace()));
            }

            return true;
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}