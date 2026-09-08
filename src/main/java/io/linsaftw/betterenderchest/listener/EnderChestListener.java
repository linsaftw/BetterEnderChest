package io.linsaftw.betterenderchest.listener;

import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EnderChestListener implements Listener {
    private final BetterEnderChestPlugin plugin;

    public EnderChestListener(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.plugin.getPermissionService().canUse(player)) {
            event.setCancelled(true);
            if (this.plugin.getTextBridge() != null) {
                this.plugin.getTextBridge().send(player, this.plugin.getPluginConfig().getMessageSource("no-permission"));
            } else {
                player.sendMessage(this.plugin.getPluginConfig().getMessage("no-permission"));
            }
            return;
        }
        event.setCancelled(true);
        this.plugin.getEnderChestService().openOwnChest(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        this.plugin.getEnderChestService().handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        this.plugin.getEnderChestService().handleInventoryDrag(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        this.plugin.getEnderChestService().handleInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getEnderChestService().handleViewerQuit(event);
    }
}
