package Events;

import UsersDatabases.User;
import Main.PlayTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @EventHandler
    public void onJoin(PlayerJoinEvent event){

            User user = new User(event.getPlayer());
            plugin.getUsersManager().addOnlineUser(user);

    }


}
