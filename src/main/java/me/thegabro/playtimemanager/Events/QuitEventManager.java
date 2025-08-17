package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX.AFKSyncManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(event.getPlayer().getName());
        if (onlineUser == null) {
            plugin.getLogger().severe("OnlineUser is null for player: " + event.getPlayer().getName());
            return;
        }

        try {
            // Finalize AFK time first if player was AFK
            if (onlineUser.isAFK()) {
                onlineUser.finalizeCurrentAFKSession();
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Update database asynchronously
                    onlineUser.updatePlayTime();
                    onlineUser.updateAFKPlayTime();
                    onlineUser.updateLastSeen();

                } catch (Exception e) {
                    plugin.getLogger().severe("Error updating database for " + onlineUser.getNickname() + ": " + e.getMessage());
                }
            });

        } catch (Exception e) {
            plugin.getLogger().warning("Error during quit preparation for " + onlineUser.getNickname() + ": " + e.getMessage());
        }

        afkSyncManager.handlePlayerQuit(onlineUser, () -> executeCleanup(onlineUser));
    }

    private void executeCleanup(OnlineUser onlineUser) {
        try {
            // These operations should be fast (memory operations only)
            onlineUsersManager.removeOnlineUser(onlineUser);
            dbUsersManager.removeUserFromCache(onlineUser.getUuid());

            // This might be slow if it involves DB operations, so do it async
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    dbUsersManager.updateCachedTopPlayers(onlineUser);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error updating top players cache: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Error during quit cleanup for player " + onlineUser.getNickname() + ": " + e.getMessage());
        }
    }
}