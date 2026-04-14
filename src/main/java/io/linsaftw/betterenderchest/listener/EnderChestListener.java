package io.linsaftw.betterenderchest.listener;

import java.util.UUID;
import java.util.logging.Logger;
import io.linsaftw.betterenderchest.BetterEnderChestPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public final class EnderChestListener implements Listener {
    private final BetterEnderChestPlugin plugin;

    public EnderChestListener(BetterEnderChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.ENDER_CHEST) {
                Player player = event.getPlayer();
                if (!this.plugin.getPermissionService().canUse(player)) {
                    player.sendMessage(this.plugin.getPluginConfig().getMessage("no-permission"));
                    event.setCancelled(true);
                } else {
                    event.setCancelled(true);
                    this.plugin.getEnderChestService().openOwnChest(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        if (this.plugin.getEnderChestService().isManagedInventory(inventory)) {
            final UUID ownerUuid = this.plugin.getEnderChestService().getManagedOwner(inventory);
            this.plugin.getEnderChestService().save(ownerUuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        this.plugin.getEnderChestService().unload(player.getUniqueId());
    }
}