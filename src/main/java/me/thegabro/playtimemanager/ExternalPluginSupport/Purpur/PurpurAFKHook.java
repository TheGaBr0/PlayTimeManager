package me.thegabro.playtimemanager.ExternalPluginSupport.Purpur;

import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.purpurmc.purpur.event.PlayerAFKEvent;

public class PurpurAFKHook implements Listener {

    private static PurpurAFKHook instance;
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    private PurpurAFKHook() {
        // Private constructor for singleton
    }

    public static PurpurAFKHook getInstance() {
        if (instance == null) {
            instance = new PurpurAFKHook();
        }
        return instance;
    }

    /**
     * Listen for AFK status changes from Purpur
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAFK(PlayerAFKEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Extra safety checks
        if (player == null) {
            return;
        }

        if (!player.isOnline()) {
            return;
        }

        boolean isNowAFK = event.isGoingAfk();
        String playerUUID = player.getUniqueId().toString();
        OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUserByUUID(playerUUID);

        // player left while afk, we can just drop this with a return
        if (onlineUser == null) {
            return;
        }

        if (isNowAFK) {
            // Player is now AFK
            afkSyncManager.handleAFKGo(onlineUser);
        } else {
            // Player is no longer AFK
            afkSyncManager.handleAFKReturn(onlineUser);
        }
    }
}