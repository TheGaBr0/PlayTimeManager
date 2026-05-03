package me.thegabro.playtimemanager.ExternalPluginSupport.placeholderAfkTime;

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

    private final HashSet<UUID> playersAfk = new HashSet<>();
    private BukkitTask task = null;

    private AFKPlaceholderManager() {
    }

    public void reset() {
        if (task != null) {
            task.cancel();
            task = null;
        }
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
                                AFKSyncManager.getInstance().handleAFKGo(user);
                                playersAfk.add(p.getUniqueId());
                            } else {
                                AFKSyncManager.getInstance().handleAFKReturn(user);
                                playersAfk.remove(p.getUniqueId());
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }), 0, 20);
    }

    public static AFKPlaceholderManager getInstance() {
        if (instance == null) {
            instance = new AFKPlaceholderManager();
        }
        return instance;
    }
}