package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventManager implements Listener {

    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    @EventHandler
    public void onJoin(PlayerJoinEvent event){

            OnlineUser onlineUser = new OnlineUser(event.getPlayer());
            onlineUsersManager.addOnlineUser(onlineUser);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, onlineUser::updateLastSeen);

            joinStreaksManager.processPlayerLogin(event.getPlayer());
            dbUsersManager.updateCachedTopPlayers(onlineUser);


    }


}
