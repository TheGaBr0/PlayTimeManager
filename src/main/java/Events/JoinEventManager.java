package Events;

import Users.OnlineUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @EventHandler
    public void onJoin(PlayerJoinEvent event){

            OnlineUser onlineUser = new OnlineUser(event.getPlayer());
            plugin.getUsersManager().addOnlineUser(onlineUser);

    }


}
