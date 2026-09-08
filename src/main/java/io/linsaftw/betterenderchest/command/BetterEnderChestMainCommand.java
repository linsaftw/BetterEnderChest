package io.linsaftw.betterenderchest.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.text.TextBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public final class BetterEnderChestMainCommand implements CommandExecutor, TabExecutor {
    private final BetterEnderChestPlugin plugin;

    public BetterEnderChestMainCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
    }

    private TextBridge texts() {
        return this.plugin.getTextBridge();
    }

    private String message(String key, String fallback) {
        final String source = this.plugin.getPluginConfig().getMessageSource(key);
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        return source;
    }

    private boolean canUse(CommandSender sender) {
        return sender.hasPermission("betterenderchest.use");
    }

    private boolean canAdmin(CommandSender sender) {
        return sender.hasPermission("betterenderchest.admin")
                || sender.hasPermission("enderchest.admin")
                || sender.isOp();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUse(sender) && !canAdmin(sender)) {
            texts().send(sender, message("no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }
        if (args.length == 0) {
            final Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("version", this.plugin.getDescription().getVersion());
            texts().send(sender, message("bec-header",
                    "<green>BetterEnderChest <white>v{version}</white> by <yellow>LinsaFTW</yellow></green>"),
                    placeholders);
            if (canAdmin(sender)) {
                texts().send(sender, message("bec-reload-entry",
                        "<yellow>reload</yellow> <gray>- <white>Reload the configuration</white></gray>"));
            }
            return true;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!canAdmin(sender)) {
                texts().send(sender, message("no-permission", "&cYou don't have permission to use this command!"));
                return true;
            }
            this.plugin.reloadConfigValues();
            texts().send(sender, message("bec-reloaded", "<green>Configuration reloaded.</green>"));
            return true;
        }
        texts().send(sender, message("bec-unknown-subcommand",
                "<red>Unknown subcommand. Use /betterenderchest for help.</red>"));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            final List<String> options = new ArrayList<String>();
            if (canAdmin(sender)) {
                options.add("reload");
            }
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            final List<String> result = new ArrayList<String>();
            for (String option : options) {
                if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(option);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
