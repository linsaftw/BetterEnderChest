package io.linsaftw.betterenderchest.command;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.service.EnderChestService;
import io.linsaftw.betterenderchest.text.TextBridge;
import io.linsaftw.betterenderchest.text.TextCodec;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EnderChestCommand implements CommandExecutor, TabCompleter {
    private final BetterEnderChestPlugin plugin;
    private final TextBridge texts;

    public EnderChestCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
        this.texts = new TextBridge(plugin, new TextCodec());
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            texts.send(sender, this.plugin.getPluginConfig().getMessageSource("player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (!this.plugin.getPermissionService().canUse(player)) {
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("no-permission"));
            return true;
        }

        try {
            EnderChestService.OpenResult result = this.plugin.getEnderChestService().openOwnChest(player);
            if (result == EnderChestService.OpenResult.STARTED) {
                texts.send(player, this.plugin.getPluginConfig().getMessageSource("enderchest-opened"));
            }
        } catch (Exception e) {
            texts.send(player, this.plugin.getPluginConfig().getMessageSource("error-loading"));
            this.plugin.getLogger().log(Level.SEVERE, "Error opening enderchest for " + player.getName(), e);
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
