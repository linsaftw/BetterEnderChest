package io.linsaftw.betterenderchest.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.service.EnderChestService;
import io.linsaftw.betterenderchest.text.TextBridge;
import io.linsaftw.betterenderchest.text.TextCodec;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EnderChestAdminCommand implements CommandExecutor, TabCompleter {
    private final BetterEnderChestPlugin plugin;
    private final TextBridge texts;

    public EnderChestAdminCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
        this.texts = new TextBridge(plugin, new TextCodec());
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            texts.send(sender, this.plugin.getPluginConfig().getMessageSource("player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!this.plugin.getPermissionService().canAdmin(player)) {
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("no-permission"));
            return true;
        }
        if (args.length != 1) {
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("admin-usage"));
            return true;
        }

        String targetPlayerName = args[0];
        final OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            Map<String, String> missing = new HashMap<String, String>();
            missing.put("player", targetPlayerName);
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("player-not-found"), missing);
            return true;
        }

        try {
            final String resolvedTargetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetPlayerName;
            EnderChestService.OpenResult result = this.plugin.getEnderChestService()
                    .openAdminChest(player, targetPlayer.getUniqueId(), resolvedTargetName);
            if (result == EnderChestService.OpenResult.STARTED) {
                Map<String, String> opened = new HashMap<String, String>();
                opened.put("player", resolvedTargetName);
                texts.send(player, this.plugin.getPluginConfig().getMessageSource("admin-enderchest-opened"), opened);
            }
        } catch (Exception e) {
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("error-loading"));
            this.plugin.getLogger().log(Level.SEVERE, "Error opening admin chest for " + targetPlayerName, e);
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
