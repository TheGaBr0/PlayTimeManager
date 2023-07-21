package Events;

import UsersDatabases.User;
import Main.PlayTimeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitEventManager implements Listener {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @EventHandler
    public void onQuit(PlayerQuitEvent event){

        User user = plugin.getUsersManager().getUserByNickname(event.getPlayer().getName());
        user.updatePlayTime();
        plugin.getUsersManager().removeOnlineUser(user);
    }

}
