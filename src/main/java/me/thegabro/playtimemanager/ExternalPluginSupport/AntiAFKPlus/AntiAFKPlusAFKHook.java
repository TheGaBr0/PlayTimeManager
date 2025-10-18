package me.thegabro.playtimemanager.ExternalPluginSupport.AntiAFKPlus;

import me.koyere.antiafkplus.api.AntiAFKPlusAPI;
import me.koyere.antiafkplus.api.events.EventRegistration;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.entity.Player;

public class AntiAFKPlusAFKHook {

    private static AntiAFKPlusAFKHook instance;
    private EventRegistration afkStateRegistration;
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    private AntiAFKPlusAFKHook() {
        // Private constructor for singleton
    }

    public static AntiAFKPlusAFKHook getInstance() {
        if (instance == null) {
            instance = new AntiAFKPlusAFKHook();
        }
        return instance;
    }

    /**
     * Register the AFK state listener with AntiAFKPlus API
     */
    public void register() {
        AntiAFKPlusAPI api = AntiAFKPlusAPI.getInstance();

        afkStateRegistration = api.registerAFKStateListener(event -> {

            PlayTimeManager.getInstance().getLogger().info("AntiAFKPlus Event");

            Player player = event.getPlayer();

            if (player == null || !player.isOnline()) {
                return;
            }

            String playerUUID = player.getUniqueId().toString();
            OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUserByUUID(playerUUID);

            // Player left while afk, we can just drop this with a return
            if (onlineUser == null) {
                return;
            }


            boolean isNowAFK = event.getToStatus().isAFK();

            if (isNowAFK) {
                // Player is now AFK
                afkSyncManager.handleAFKGo(onlineUser);
            } else {
                // Player is no longer AFK
                afkSyncManager.handleAFKReturn(onlineUser);
            }
        });
    }

    /**
     * Unregister the listener when plugin disables
     */
    public void unregister() {
        if (afkStateRegistration != null) {
            AntiAFKPlusAPI api = AntiAFKPlusAPI.getInstance();
            api.unregisterListener(afkStateRegistration);
            afkStateRegistration = null;
        }
    }
}