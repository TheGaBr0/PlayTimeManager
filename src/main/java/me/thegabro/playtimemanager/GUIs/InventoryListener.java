package me.thegabro.playtimemanager.GUIs;

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
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Universal inventory listener that handles all custom GUI interactions
 * Supports multiple GUI types through a common interface
 */

public class InventoryListener implements Listener {

    private static InventoryListener instance;
    private static boolean isRegistered = false;

    // Registry of all active GUIs by player UUID
    private final Map<UUID, CustomGUI> activeGuis = new HashMap<>();

    private InventoryListener() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance and ensure it's registered
     */
    public static InventoryListener getInstance() {
        if (instance == null) {
            instance = new InventoryListener();
        }

        if (!isRegistered) {
            Bukkit.getPluginManager().registerEvents(instance, PlayTimeManager.getInstance());
            isRegistered = true;
        }

        return instance;
    }

    /**
     * Register a GUI as active for a player
     */
    public void registerGUI(UUID playerUuid, CustomGUI gui) {
        activeGuis.put(playerUuid, gui);
    }

    /**
     * Unregister a GUI for a player
     */
    public void unregisterGUI(UUID playerUuid) {
        activeGuis.remove(playerUuid);
    }

    /**
     * Check if a player has an active GUI
     */
    public boolean hasActiveGUI(UUID playerUuid) {
        return activeGuis.containsKey(playerUuid);
    }

    /**
     * Get the active GUI for a player
     */
    public CustomGUI getActiveGUI(UUID playerUuid) {
        return activeGuis.get(playerUuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();

        // Check if this is one of our custom GUIs
        if (holder instanceof CustomGUI && e.getWhoClicked() instanceof Player player) {
            e.setCancelled(true);

            CustomGUI gui = activeGuis.get(player.getUniqueId());
            if (gui != null && gui == holder) {
                gui.onGUIClick(player, e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof CustomGUI && event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            CustomGUI gui = activeGuis.get(playerId);

            if (gui != null && gui == holder) {
                gui.onGUIClose(player);

                unregisterGUI(playerId);

                PlayTimeManager.getInstance().getSessionManager().endSession(playerId);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        CustomGUI gui = activeGuis.get(playerId);

        if (gui != null) {
            gui.onPlayerQuit(event.getPlayer());
            unregisterGUI(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof CustomGUI) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player player = e.getPlayer();
        if (hasActiveGUI(player.getUniqueId())) {
            // Prevent interacting with entities while GUI is open
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSwap(PlayerSwapHandItemsEvent e) {
        Player player = e.getPlayer();
        if (hasActiveGUI(player.getUniqueId())) {
            // Prevent swap hands exploit
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin().equals(PlayTimeManager.getInstance())) {
            // Clean up all open inventories when plugin disables
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof CustomGUI) {
                    player.closeInventory();
                }
            }
            activeGuis.clear();
        }
    }
}
