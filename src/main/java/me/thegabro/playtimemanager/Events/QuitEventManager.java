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
            plugin.getLogger().severe("OnlineUser is null for player: " + event.getPlayer().getName() +
                    ". Please report this issue to the plugin developer.");
            return;
        }

        onlineUser.updatePlayTime();
        onlineUser.updateLastSeen();

        // Use sync manager to handle quit with potential AFK coordination
        afkSyncManager.handlePlayerQuit(onlineUser, () -> executeCleanup(onlineUser));
    }

    private void executeCleanup(OnlineUser onlineUser) {
        onlineUsersManager.removeOnlineUser(onlineUser);

        // Remove the user from the cache to ensure fresh data on next access
        // This prevents using stale cached data when the user rejoins
        dbUsersManager.removeUserFromCache(onlineUser.getUuid());

        dbUsersManager.updateCachedTopPlayers(onlineUser);
    }

}
