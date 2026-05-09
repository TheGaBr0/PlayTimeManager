package me.thegabro.playtimemanager.ExternalPluginSupport.AFKPlus;

import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.lapismc.afkplus.api.AFKStartEvent;
import net.lapismc.afkplus.api.AFKStopEvent;
import net.lapismc.afkplus.playerdata.AFKPlusPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AFKPlusAFKHook implements Listener {

    private static AFKPlusAFKHook instance;
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    private AFKPlusAFKHook() {
        // Private constructor for singleton
    }

    public static AFKPlusAFKHook getInstance() {
        if (instance == null) {
            instance = new AFKPlusAFKHook();
        }
        return instance;
    }

    /**
     * Listen for AFK start events from AFKPlus
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAFKStart(AFKStartEvent event) {
        if (event.isCancelled()) return;

        AFKPlusPlayer afkPlusPlayer = event.getPlayer();
        Player player = Bukkit.getPlayer(afkPlusPlayer.getUUID());

        if (player == null || !player.isOnline()) return;

        String playerUUID = player.getUniqueId().toString();
        OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUserByUUID(playerUUID);

        // Player left while event was firing, safely ignore
        if (onlineUser == null) return;

        afkSyncManager.handleAFKGo(onlineUser);
    }

    /**
     * Listen for AFK stop events from AFKPlus
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAFKStop(AFKStopEvent event) {
        if (event.isCancelled()) return;

        AFKPlusPlayer afkPlusPlayer = event.getPlayer();
        Player player = Bukkit.getPlayer(afkPlusPlayer.getUUID());

        if (player == null || !player.isOnline()) return;

        String playerUUID = player.getUniqueId().toString();
        OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUserByUUID(playerUUID);

        // Player left while event was firing, safely ignore
        if (onlineUser == null) return;

        afkSyncManager.handleAFKReturn(onlineUser);
    }
}