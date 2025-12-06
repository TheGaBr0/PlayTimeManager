package me.thegabro.playtimemanager.ExternalPluginSupport;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

public class AFKSyncManager {
    private static AFKSyncManager instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    private AFKSyncManager() {}

    public static AFKSyncManager getInstance() {
        if (instance == null) {
            instance = new AFKSyncManager();
        }
        return instance;
    }

    /**
     * Called when AFK return event occurs (only for online players)
     * Updates AFK time and sets status to false
     */
    public void handleAFKReturn(OnlineUser user) {
        if (user == null) {
            // This can happen if player disconnects while AFK and EssentialsX fires the return event
            return;
        }
        plugin.getLogger().warning("not anymore AFK status detected for " + user.getNickname());

        try {
            if (user.isAFK()) {
                user.finalizeCurrentAFKSession();
                user.updateAFKPlayTime();
                user.setAFK(false);

            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error updating AFK return for player " + user.getNickname() + ": " + e.getMessage());
        }
    }

    /**
     * Called when player becomes AFK
     * IMMEDIATELY updates AFK playtime to maintain precision
     */
    public void handleAFKGo(OnlineUser user) {
        if (user == null) {
            return;
        }
        plugin.getLogger().warning("AFK status detected for " + user.getNickname());

        try {
            if (!user.isAFK()) {
                user.updateAFKPlayTime();
                user.setAFK(true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error setting AFK status for player " + user.getNickname() + ": " + e.getMessage());
        }
    }
}