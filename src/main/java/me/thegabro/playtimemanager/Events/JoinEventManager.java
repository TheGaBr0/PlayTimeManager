package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinEventManager implements Listener {

    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    @EventHandler
    public void onJoin(PlayerJoinEvent event){

        OnlineUser.createOnlineUserAsync(event.getPlayer(), onlineUser -> {

                // Check if player already left before we finished loading
                if (!event.getPlayer().isOnline()) {
                    // Still need to write lastSeen and flush to DB even though they're gone
                    onlineUser.updateAllOnQuitAsync(
                            event.getPlayer().getStatistic(Statistic.PLAY_ONE_MINUTE), null
                    );
                    return;
                }

                onlineUsersManager.addOnlineUser(onlineUser);
                joinStreaksManager.processPlayerLogin(onlineUser);
                goalsManager.processPlayerLogin(onlineUser);
                onlineUser.updateLastSeen();
                dbUsersManager.markAsExistent(onlineUser.getNickname());
                dbUsersManager.updateCachedTopPlayers(onlineUser);
            });


    }


}
