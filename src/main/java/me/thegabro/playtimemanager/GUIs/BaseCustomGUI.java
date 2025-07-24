package me.thegabro.playtimemanager.GUIs;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.entity.Player;


//Abstract base class for custom GUIs

public abstract class BaseCustomGUI implements CustomGUI {

    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final Player sender;
    protected final String sessionToken;
    protected org.bukkit.inventory.Inventory inv;

    public BaseCustomGUI(Player sender, String sessionToken) {
        this.sender = sender;
        this.sessionToken = sessionToken;
    }

    /**
     * Open the inventory and register with the listener
     */
    public void openInventory() {
        // Register with the universal listener
        InventoryListener.getInstance().registerGUI(sender.getUniqueId(), this);

        // Open the inventory
        sender.openInventory(inv);
    }

    @Override
    public String getSessionToken() {
        return sessionToken;
    }

    @Override
    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(sender.getUniqueId());
    }

    @Override
    public org.bukkit.inventory.Inventory getInventory() {
        return inv;
    }

    /**
     * Validate session token for security
     */
    protected boolean validateSession() {
        return plugin.getSessionManager().validateSession(sender.getUniqueId(), sessionToken);
    }

    /**
     * Handle invalid session - close GUI and log warning
     */
    protected void handleInvalidSession() {
        plugin.getLogger().warning("Player " + sender.getName() + " attempted GUI action with invalid session token!");
        sender.closeInventory();
    }
}
