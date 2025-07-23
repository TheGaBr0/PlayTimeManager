package me.thegabro.playtimemanager.GUIs;

import me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.UUID;

import static me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui.activeGuis;

public class InventoryListener implements Listener {
        @EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (e.getInventory().getHolder() instanceof RewardsInfoGui) {
                e.setCancelled(true);

                if (e.getWhoClicked() instanceof Player player) {
                    RewardsInfoGui gui = activeGuis.get(player.getUniqueId());
                    if (gui != null) {
                        gui.onGUIClick(player, e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);
                    }
                }
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() instanceof RewardsInfoGui && event.getPlayer() instanceof Player player) {
                // Clean up
                activeGuis.remove(player.getUniqueId());
                PlayTimeManager.getInstance().getSessionManager().endSession(player.getUniqueId());
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            // Clean up on player disconnect
            UUID playerId = event.getPlayer().getUniqueId();
            activeGuis.remove(playerId);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onInventoryDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof RewardsInfoGui) {
                e.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
            Player player = e.getPlayer();
            if (activeGuis.containsKey(player.getUniqueId())) {
                // Prevent interacting with entities while GUI is open
                e.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onItemSwap(PlayerSwapHandItemsEvent e) {
            Player player = e.getPlayer();
            if (activeGuis.containsKey(player.getUniqueId())) {
                // Prevent swap hands exploit
                e.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(PluginDisableEvent e) {
            if (e.getPlugin().equals(PlayTimeManager.getInstance())) {
                // Clean up all open inventories when plugin disables
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof RewardsInfoGui) {
                        player.closeInventory();
                    }
                }
                activeGuis.clear();
            }
        }
    }
