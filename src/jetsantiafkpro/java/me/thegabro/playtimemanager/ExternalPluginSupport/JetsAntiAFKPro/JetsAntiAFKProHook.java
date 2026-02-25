package me.thegabro.playtimemanager.ExternalPluginSupport.JetsAntiAFKPro;

import me.jet315.antiafkpro.AntiAFKProAPI;
import me.jet315.antiafkpro.JetsAntiAFKPro;
import me.jet315.antiafkpro.manager.AFKPlayer;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JetsAntiAFKProHook {

    private static JetsAntiAFKProHook instance;

    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();
    private final AntiAFKProAPI antiAFKProAPI;
    private final Map<UUID, Boolean> lastAFKState = new ConcurrentHashMap<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    private JetsAntiAFKProHook() {
        // Create a new instance of the API
        JetsAntiAFKPro antiAFKPlugin = (JetsAntiAFKPro) Bukkit.getPluginManager().getPlugin("JetsAntiAFKPro");
        this.antiAFKProAPI = antiAFKPlugin.getAntiAFKProAPI();
    }

    public static JetsAntiAFKProHook getInstance() {
        if (instance == null) {
            instance = new JetsAntiAFKProHook();
        }
        return instance;
    }

    public void init(){
        startPolling(plugin);
    }

    private void startPolling(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    handlePlayer(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handlePlayer(Player player) {

        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());

        if (onlineUser == null) {
            lastAFKState.remove(uuid);
            return;
        }

        AFKPlayer afkPlayer = antiAFKProAPI.getAFKPlayer(player);
        if (afkPlayer == null) return;

        boolean isNowAFK = afkPlayer.getSecondsAFK() >= 120 || afkPlayer.isPlayerAFKByCommand();

        Boolean wasAFK = lastAFKState.put(uuid, isNowAFK);

        // First time seeing this player - just initialize, don't trigger events
        if (wasAFK == null) {
            return;
        }

        // No change in state
        if (wasAFK.equals(isNowAFK)) {
            return;
        }

        // State changed - trigger appropriate event
        if (isNowAFK) {
            //plugin.getLogger().info(player.getName() + " has gone afk");
            afkSyncManager.handleAFKGo(onlineUser);
        } else {
            //plugin.getLogger().info(player.getName() + " has returned from afk");
            afkSyncManager.handleAFKReturn(onlineUser);
        }
    }

    public void handleQuit(Player player) {
        plugin.getLogger().info(player.getName()+" removed from afk");
        lastAFKState.remove(player.getUniqueId());
    }
}