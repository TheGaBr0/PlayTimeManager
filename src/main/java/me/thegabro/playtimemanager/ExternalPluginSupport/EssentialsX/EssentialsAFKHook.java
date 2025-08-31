package me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX;

import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.ess3.api.IUser;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EssentialsAFKHook implements Listener {

    private static EssentialsAFKHook instance;
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    private EssentialsAFKHook() {
        // Private constructor for singleton
    }

    public static EssentialsAFKHook getInstance() {
        if (instance == null) {
            instance = new EssentialsAFKHook();
        }
        return instance;
    }

    /**
     * Listen for AFK status changes from EssentialsX
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAfkStatusChange(AfkStatusChangeEvent event) {
        if (event.isCancelled()) return;

        IUser user = event.getAffected();
        Player player = user.getBase();

        // Extra safety checks
        if (player == null) {
            return;
        }

        if (!player.isOnline()) {
            return;
        }

        boolean isNowAFK = event.getValue();
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