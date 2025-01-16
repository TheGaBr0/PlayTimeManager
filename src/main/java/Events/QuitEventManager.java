package Events;

import Users.OnlineUser;
import Users.OnlineUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @EventHandler
    public void onQuit(PlayerQuitEvent event){

        OnlineUser onlineUser = plugin.getOnlineUsersManager().getOnlineUser(event.getPlayer().getName());
        onlineUser.updateDB();
        plugin.getOnlineUsersManager().removeOnlineUser(onlineUser);
        plugin.getDbUsersManager().updateCachedTopPlayers(onlineUser);
    }

}
