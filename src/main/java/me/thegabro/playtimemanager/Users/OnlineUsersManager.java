package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public void initialize() {
        startGoalCheckSchedule();
        startDBUpdateSchedule();
    }

    public Map<String, OnlineUser> getOnlineUsersByUUID() {
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
        Bukkit.getOnlinePlayers().forEach(player ->
                OnlineUser.createOnlineUserAsync(player, this::addOnlineUser));
    }

    public OnlineUser getOnlineUser(String nickname) {
        return onlineUsersByName.get(nickname.toLowerCase());
    }

    public OnlineUser getOnlineUserByUUID(String uuid) {
        return onlineUsersByUUID.get(uuid);
    }

    public void startGoalCheckSchedule() {
        for (Goal g : goalsManager.getGoals()) {
            if (g.isActive()) {
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
                updateAllOnlineUsersPlaytimeAsync();
            }
        }.runTaskTimer(plugin, 0, DB_UPDATE_INTERVAL);
    }

    /**
     * Persists playtime, AFK time, and last-seen for every online user in a single async task.
     */
    public CompletableFuture<Void> updateAllOnlineUsersPlaytimeAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Capture snapshots on whichever thread calls this (normally the main thread or
        // the periodic scheduler task). Snapshots decouple the DB writes from the
        // potentially-stale player statistic object.
        List<UserSnapshot> snapshots = new ArrayList<>();
        for (OnlineUser user : onlineUsersByName.values()) {
            try {
                long snapshot = user.getPlayerInstance().getStatistic(Statistic.PLAY_ONE_MINUTE);
                snapshots.add(new UserSnapshot(user, snapshot));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to read statistic for " + user.getNickname() + ": " + e.getMessage());
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UserSnapshot s : snapshots) {
                try {
                    // Write all three values in one async block per user — no extra threads
                    s.user().updatePlayTimeWithSnapshot(s.snapshot());
                    s.user().updateAFKPlayTimeWithSnapshot(s.snapshot());
                    s.user().updateLastSeen();
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to update playtime for " + s.user().getNickname()
                            + ": " + e.getMessage());
                }
            }
            future.complete(null);
        });

        return future;
    }

    public void stopSchedules() {
        Optional.ofNullable(dbUpdateSchedule).ifPresent(BukkitTask::cancel);
    }

    /**
     * Lightweight value type to carry a playtime snapshot alongside its user,
     * avoiding repeated stat reads across thread boundaries.
     */
    private record UserSnapshot(OnlineUser user, long snapshot) {}
}