package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineUsersManager {
    private static volatile OnlineUsersManager instance;
    private BukkitTask dbUpdateSchedule;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final Map<String, OnlineUser> onlineUsersByName;
    private final Map<String, OnlineUser> onlineUsersByUUID;

    private static final int DB_UPDATE_INTERVAL = 300 * 20; // 5 minutes in ticks

    private OnlineUsersManager() {
        this.onlineUsersByName = new ConcurrentHashMap<>();
        this.onlineUsersByUUID = new ConcurrentHashMap<>();
        loadOnlineUsers();
    }

    public void initialize(){
        startGoalCheckSchedule();
        startDBUpdateSchedule();
    }

    public static OnlineUsersManager getInstance() {
        if (instance == null) {
            synchronized (OnlineUsersManager.class) {
                if (instance == null) {
                    instance = new OnlineUsersManager();
                }
            }
        }
        return instance;
    }

    public Map<String, OnlineUser> getOnlineUsersByUUID(){
        return onlineUsersByUUID;
    }

    public void addOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.put(onlineUser.getNickname().toLowerCase(), onlineUser);
        onlineUsersByUUID.put(onlineUser.getUuid(), onlineUser);
    }

    public void removeOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.remove(onlineUser.getNickname().toLowerCase());
        onlineUsersByUUID.remove(onlineUser.getUuid());
    }

    public void loadOnlineUsers() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            OnlineUser onlineUser = new OnlineUser(player);
            addOnlineUser(onlineUser);
        });
    }

    public OnlineUser getOnlineUser(String nickname) {
        return onlineUsersByName.get(nickname.toLowerCase());
    }

    public OnlineUser getOnlineUserByUUID(String uuid) {
        return onlineUsersByUUID.get(uuid);
    }

    public void startGoalCheckSchedule() {

        Set<Goal> goals = goalsManager.getGoals();
        for(Goal g : goals){
            if(g.isActive()){
                g.restartCompletionCheckTask();
            }
        }

    }


    private void startDBUpdateSchedule() {
        if (dbUpdateSchedule != null) {
            dbUpdateSchedule.cancel();
        }

        dbUpdateSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllOnlineUsersPlaytime();
            }
        }.runTaskTimer(plugin, 0, DB_UPDATE_INTERVAL);
    }

    public CompletableFuture<Void> updateAllOnlineUsersPlaytime() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                onlineUsersByName.values().forEach(user -> {
                    try {
                        user.updatePlayTime();
                        user.updateAFKPlayTime();
                        user.updateLastSeen();
                    } catch (Exception e) {
                        plugin.getLogger().severe(String.format("Failed to update playtime for user %s: %s",
                                user.getNickname(), e.getMessage()));
                    }
                });
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }



    public void stopSchedules() {
        Optional.ofNullable(dbUpdateSchedule).ifPresent(BukkitTask::cancel);
    }
}
