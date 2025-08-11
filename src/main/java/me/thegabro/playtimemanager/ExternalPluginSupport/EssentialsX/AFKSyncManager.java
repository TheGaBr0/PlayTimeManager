package me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AFKSyncManager {
    private static AFKSyncManager instance;
    private final Map<String, PlayerSyncState> playerStates = new ConcurrentHashMap<>();
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
     */
    public void handlePlayerQuit(OnlineUser onlineUser, Runnable quitCleanup) {
        String playerUUID = onlineUser.getUuid();

        // If AFK detection is disabled, no AFK events can occur - execute immediately
        if (!plugin.isAfkDetectionConfigured()) {
            quitCleanup.run();
            return;
        }

        // AFK detection is enabled, so we need to coordinate
        PlayerSyncState state = playerStates.computeIfAbsent(playerUUID, k -> new PlayerSyncState());

        synchronized (state) {
            state.quitEventOccurred = true;
            state.quitCleanup = quitCleanup;

            // If AFK event already completed, execute cleanup immediately
            if (state.afkEventCompleted) {
                executeAndCleanup(playerUUID, state);
            } else {
                // Wait for AFK event, but set timeout in case it never comes
                scheduleTimeout(playerUUID, state);
            }
        }
    }

    /**
     * Called when AFK return event occurs
     */
    public void handleAFKReturn(OnlineUser user) {
        // Execute AFK logic first
        if (user != null) {
            user.setAFK(false);
            user.updateAFKPlayTime();
        }

        PlayerSyncState state = playerStates.get(user.getUuid());
        if (state == null) {
            // No quit event waiting, AFK return completed independently
            return;
        }

        synchronized (state) {
            state.afkEventCompleted = true;

            // If quit event already happened, execute cleanup now
            if (state.quitEventOccurred && state.quitCleanup != null) {
                executeAndCleanup(user.getUuid(), state);
            }
            // If quit hasn't happened yet, just mark AFK as completed and wait
        }
    }

    /**
     * Called when player becomes AFK
     * Note: This method will ONLY be called if AFK detection is configured
     */
    public void handleAFKGo(OnlineUser user) {
        // AFK go doesn't need synchronization with quit, just execute immediately
        if (user != null) {
            user.setAFK(true);
        }
    }

    /**
     * Execute cleanup and remove player state
     */
    private void executeAndCleanup(String playerUUID, PlayerSyncState state) {
        try {
            if (state.quitCleanup != null) {
                state.quitCleanup.run();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error during quit cleanup for player " + playerUUID + ": " + e.getMessage());
        } finally {
            playerStates.remove(playerUUID);
            if (state.timeoutTask != null) {
                state.timeoutTask.cancel();
            }
        }
    }

    /**
     * Schedule timeout to prevent hanging if AFK event never comes
     */
    private void scheduleTimeout(String playerUUID, PlayerSyncState state) {
        state.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            synchronized (state) {
                if (playerStates.containsKey(playerUUID)) {
                    plugin.getLogger().info("Timeout reached for player " + playerUUID + " - AFK event never occurred, executing quit cleanup");
                    executeAndCleanup(playerUUID, state);
                }
            }
        }, 100L); // 5 seconds timeout
    }

    /**
     * Internal class to track synchronization state for each player
     */
    private static class PlayerSyncState {
        boolean quitEventOccurred = false;
        boolean afkEventCompleted = false;
        Runnable quitCleanup = null;
        BukkitTask timeoutTask = null;
    }
}