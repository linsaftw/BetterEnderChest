package io.linsaftw.betterenderchest.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EnderChestAdminCommand implements CommandExecutor, TabCompleter {
    private final BetterEnderChestPlugin plugin;

    public EnderChestAdminCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getPluginConfig().getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!this.plugin.getPermissionService().canAdmin(player)) {
            player.sendMessage(this.plugin.getPluginConfig().getMessage("no-permission"));
            return true;
        } else if (args.length != 1) {
            player.sendMessage(this.plugin.getPluginConfig().getMessage("admin-usage"));
            return true;
        }

        String targetPlayerName = args[0];
        final OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            player.sendMessage(this.plugin.getPluginConfig().getMessage("player-not-found", "player", targetPlayerName));
            return true;
        }

        try {
            final String resolvedTargetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetPlayerName;
            this.plugin.getEnderChestService().openAdminChest(player, targetPlayer.getUniqueId(), resolvedTargetName);
        } catch (Exception e) {
            player.sendMessage(this.plugin.getPluginConfig().getMessage("error-loading"));
            this.plugin.getLogger().severe("Error opening admin chest for " + targetPlayerName + ": " + e.getMessage());
            this.plugin.getLogger().severe("Stack trace: ");
            this.plugin.getLogger().severe(Arrays.toString(e.getStackTrace()));
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partialName = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partialName))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}