package io.linsaftw.betterenderchest.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import io.linsaftw.betterenderchest.service.EnderChestService;
import io.linsaftw.betterenderchest.text.TextBridge;
import io.linsaftw.betterenderchest.utils.FoliaAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EnderChestAdminCommand implements CommandExecutor, TabCompleter {
    private final BetterEnderChestPlugin plugin;
    private final FoliaAPI folia;

    public EnderChestAdminCommand(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
        this.folia = new FoliaAPI(plugin);
    }

    static boolean isClearAction(final String token) {
        return "clear".equalsIgnoreCase(token);
    }

    static boolean isViewAction(final String token) {
        return "view".equalsIgnoreCase(token);
    }

    static List<String> completeSecondToken(final String partial) {
        final String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<String>(2);
        if ("clear".startsWith(prefix)) {
            matches.add("clear");
        }
        if ("view".startsWith(prefix)) {
            matches.add("view");
        }
        return matches;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.plugin.getPermissionService().canAdmin(sender)) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || args.length > 2) {
            send(sender, "admin-usage");
            return true;
        }
        final boolean view;
        if (args.length == 1) {
            view = true;
        } else if (isViewAction(args[1])) {
            view = true;
        } else if (isClearAction(args[1])) {
            view = false;
        } else {
            send(sender, "admin-usage");
            return true;
        }

        final String targetPlayerName = args[0];
        final OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            Map<String, String> missing = new HashMap<String, String>();
            missing.put("player", targetPlayerName);
            send(sender, "player-not-found", missing);
            return true;
        }
        final String resolvedTargetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetPlayerName;
        if (view) {
            return openView(sender, targetPlayer.getUniqueId(), resolvedTargetName);
        }
        return clearChest(sender, targetPlayer.getUniqueId(), resolvedTargetName);
    }

    private boolean openView(final CommandSender sender, final UUID ownerUuid, final String ownerName) {
        if (!(sender instanceof Player)) {
            send(sender, "player-only");
            return true;
        }
        final Player player = (Player) sender;
        try {
            EnderChestService.OpenResult result = this.plugin.getEnderChestService()
                    .openAdminChest(player, ownerUuid, ownerName);
            if (result == EnderChestService.OpenResult.STARTED) {
                Map<String, String> opened = new HashMap<String, String>();
                opened.put("player", ownerName);
                send(player, "admin-enderchest-opened", opened);
            }
        } catch (Exception e) {
            send(player, "error-loading");
            this.plugin.getLogger().log(Level.SEVERE, "Error opening admin chest for " + ownerName, e);
        }
        return true;
    }

    private boolean clearChest(final CommandSender sender, final UUID ownerUuid, final String ownerName) {
        final EnderChestService service = this.plugin.getEnderChestService();
        if (service == null) {
            send(sender, "error-saving");
            return true;
        }
        final CompletableFuture<EnderChestService.ClearResult> future;
        try {
            future = service.clearChest(ownerUuid, ownerName);
        } catch (Exception failed) {
            send(sender, "error-saving");
            return true;
        }
        if (future == null) {
            send(sender, "error-saving");
            return true;
        }
        future.whenComplete((result, failure) -> {
            if (failure != null) {
                sendFeedback(sender, "error-saving", playerPlaceholder(ownerName));
                return;
            }
            if (result == EnderChestService.ClearResult.CLEARED) {
                sendFeedback(sender, "admin-chest-cleared", playerPlaceholder(ownerName));
            } else if (result == EnderChestService.ClearResult.BUSY) {
                sendFeedback(sender, "admin-clear-busy", playerPlaceholder(ownerName));
            } else {
                sendFeedback(sender, "error-saving", playerPlaceholder(ownerName));
            }
        });
        return true;
    }

    private void sendFeedback(final CommandSender sender, final String key, final Map<String, String> placeholders) {
        if (!this.plugin.isEnabled()) {
            return;
        }
        final Runnable deliver = new Runnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    return;
                }
                send(sender, key, placeholders);
            }
        };
        if (sender instanceof Player && ((Player) sender).isOnline()) {
            this.folia.runEntity((Player) sender, deliver);
        } else {
            this.folia.runGlobal(deliver);
        }
    }

    private void send(final CommandSender sender, final String key) {
        final TextBridge texts = this.plugin.getTextBridge();
        if (texts == null) {
            return;
        }
        texts.send(sender, this.plugin.getPluginConfig().getMessageSource(key));
    }

    private void send(final CommandSender sender, final String key, final Map<String, String> placeholders) {
        final TextBridge texts = this.plugin.getTextBridge();
        if (texts == null) {
            return;
        }
        texts.send(sender, this.plugin.getPluginConfig().getMessageSource(key), placeholders);
    }

    private static Map<String, String> playerPlaceholder(final String playerName) {
        final Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("player", playerName);
        return placeholders;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!this.plugin.getPermissionService().canAdmin(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            final String partialName = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(partialName))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return completeSecondToken(args[1]);
        }
        return Collections.emptyList();
    }
}
