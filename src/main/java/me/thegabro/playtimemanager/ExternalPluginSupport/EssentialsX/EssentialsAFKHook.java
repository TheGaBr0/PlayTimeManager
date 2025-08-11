package me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX;

import me.thegabro.playtimemanager.PlayTimeManager;
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

        if (player == null || !player.isOnline()) return;

        boolean isNowAFK = event.getValue();

        if (isNowAFK) {
            // Player is now AFK
            onPlayerGoAFK(player);
        } else {
            // Player is no longer AFK
            onPlayerReturnFromAFK(player);
        }
    }

    /**
     * Called when a player goes AFK
     */
    protected void onPlayerGoAFK(Player player) {
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
        user.setAFK(true);
    }

    /**
     * Called when a player returns from AFK
     */
    protected void onPlayerReturnFromAFK(Player player) {
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
        user.setAFK(false);
        user.updateAFKPlayTime();
    }
}