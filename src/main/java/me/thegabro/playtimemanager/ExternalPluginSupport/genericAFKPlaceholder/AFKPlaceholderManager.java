package me.thegabro.playtimemanager.ExternalPluginSupport.genericAFKPlaceholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.UUID;

public class AFKPlaceholderManager {
    private static AFKPlaceholderManager instance;
    private final AFKSyncManager afkSyncManager = AFKSyncManager.getInstance();

    public static AFKPlaceholderManager getInstance() {
        if (instance == null) instance = new AFKPlaceholderManager();
        return instance;
    }

    private final HashSet<UUID> playersAfk = new HashSet<>();
    private BukkitTask task = null;

    private AFKPlaceholderManager() {
    }

    public void reset() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        playersAfk.clear(); // clear stale state so re-enable starts fresh
    }

    public void start(String placeholder, String afkVal) {
        if (task != null) task.cancel();

        task = Bukkit.getScheduler().runTaskTimer(PlayTimeManager.getInstance(), () ->
                Bukkit.getOnlinePlayers().forEach(p -> {
                    String resStr = PlaceholderAPI.setPlaceholders(p, placeholder);

                    try {
                        boolean isAfk = resStr.equals(afkVal);
                        boolean hasChanged = isAfk != playersAfk.contains(p.getUniqueId());

                        if (hasChanged) {
                            String uuid = p.getUniqueId().toString();
                            OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid);

                            if (isAfk) {
                                afkSyncManager.handleAFKGo(user);
                                playersAfk.add(p.getUniqueId());
                            } else {
                                afkSyncManager.handleAFKReturn(user);
                                playersAfk.remove(p.getUniqueId());
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }), 0, 20);
    }
}