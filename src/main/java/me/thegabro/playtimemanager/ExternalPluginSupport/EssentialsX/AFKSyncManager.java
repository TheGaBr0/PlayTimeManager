package me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX;

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
     * Called when player quit event occurs
     * Ensures proper cleanup without interfering with playtime calculations
     */
    public void handlePlayerQuit(OnlineUser onlineUser, Runnable quitCleanup) {
        if (onlineUser == null) {
            return;
        }

        try {
            // Reset AFK status to prevent issues
            if (onlineUser.isAFK()) {
                onlineUser.setAFK(false);
            }

            // Execute cleanup
            quitCleanup.run();
        } catch (Exception e) {
            plugin.getLogger().severe("Error in handlePlayerQuit for " +
                    onlineUser.getNickname() + ": " + e.getMessage());
            // Still try to run cleanup even if there was an error
            try {
                quitCleanup.run();
            } catch (Exception cleanupError) {
                plugin.getLogger().severe("Critical error during quit cleanup: " + cleanupError.getMessage());
            }
        }
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

        try {
            if (user.isAFK()) {
                user.setAFK(false);
                user.updateAFKPlayTime();
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