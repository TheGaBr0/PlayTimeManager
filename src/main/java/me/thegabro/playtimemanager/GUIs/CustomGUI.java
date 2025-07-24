package me.thegabro.playtimemanager.GUIs;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

//Interface that all custom GUIs should implement

interface CustomGUI extends InventoryHolder {

    /**
     * Handle GUI click events
     */
    void onGUIClick(Player player, int slot, org.bukkit.inventory.ItemStack clickedItem,
                    org.bukkit.event.inventory.InventoryAction action,
                    InventoryClickEvent event);

    /**
     * Handle GUI close events
     * Called before the GUI is unregistered
     */
    default void onGUIClose(Player player) {
        // Default implementation - override if needed
    }

    /**
     * Handle player quit events
     * Called when a player with an open GUI disconnects
     */
    default void onPlayerQuit(Player player) {
        // Default implementation - override if needed
    }

    /**
     * Get the session token for this GUI (for security validation)
     */
    String getSessionToken();

    /**
     * Check if the player is the owner of this GUI
     */
    boolean isOwner(Player player);
}
