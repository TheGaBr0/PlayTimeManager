package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @EventHandler
    public void onQuit(PlayerQuitEvent event){

        OnlineUser onlineUser = plugin.getOnlineUsersManager().getOnlineUser(event.getPlayer().getName());
        onlineUser.updateLastSeen();
        onlineUser.updateDB();
        plugin.getOnlineUsersManager().removeOnlineUser(onlineUser);

        // Remove the user from the cache to ensure fresh data on next access
        // This prevents using stale cached data when the user rejoins
        plugin.getDbUsersManager().removeUserFromCache(onlineUser.getUuid());

        plugin.getDbUsersManager().updateCachedTopPlayers(onlineUser);

    }

}
