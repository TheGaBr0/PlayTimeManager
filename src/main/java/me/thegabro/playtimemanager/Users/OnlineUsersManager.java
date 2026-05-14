package me.thegabro.playtimemanager.Users;

import me.clip.placeholderapi.PlaceholderAPI;
import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class OnlineUsersManager {
    private static volatile OnlineUsersManager instance;
    private BukkitTask dbUpdateSchedule;
    private BukkitTask vanishPollSchedule;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final Configuration config = Configuration.getInstance();
    private final Map<String, OnlineUser> onlineUsersByName;
    private final Map<String, OnlineUser> onlineUsersByUUID;
    // CopyOnWriteArrayList: addVanishedPlayer/removeVanishedPlayer run on main thread,
    // but isCurrentlyVanished is also called from async contexts (leaderboard update).
    private final List<OnlineUser> vanishedPlayers;
    // ConcurrentHashMap: snapshot reads happen from async threads (leaderboard update).
    private final Map<String, DBUser> vanishSnapshots;

    private static final int DB_UPDATE_INTERVAL = 300 * 20; // 5 minutes in ticks

    private OnlineUsersManager() {
        this.onlineUsersByName = new ConcurrentHashMap<>();
        this.onlineUsersByUUID = new ConcurrentHashMap<>();
        this.vanishedPlayers = new CopyOnWriteArrayList<>();
        this.vanishSnapshots = new ConcurrentHashMap<>();
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
        startVanishPollSchedule();
    }

    public Map<String, OnlineUser> getOnlineUsersByUUID() {
        return onlineUsersByUUID;
    }

    public ArrayList<OnlineUser> getVanishedPlayers(){ return new ArrayList<>(vanishedPlayers); }

    /** Returns true if the player is tracked as currently vanished (fast in-memory check). */
    public boolean isCurrentlyVanished(OnlineUser onlineUser) {
        return vanishedPlayers.contains(onlineUser);
    }

    /** Returns the frozen snapshot DBUser for a vanished player, or null if not ready yet. */
    public DBUser getVanishSnapshot(String uuid) {
        return vanishSnapshots.get(uuid);
    }

    /**
     * Returns the effective DBUser for placeholder self-lookups (no explicit target nickname).
     * Vanished players return their frozen snapshot so observers see them as offline.
     * Returns null when the player is not online or the snapshot is not yet ready.
     */
    public DBUser getEffectiveUser(String name) {
        OnlineUser onlineUser = getOnlineUser(name);
        if (onlineUser == null) return null;
        if (isCurrentlyVanished(onlineUser)) {
            return vanishSnapshots.get(onlineUser.getUuid());
        }
        return onlineUser;
    }

    /**
     * Checks the external vanish plugin via PlaceholderAPI to determine if the player
     * is currently vanished. Use isCurrentlyVanished for fast in-memory checks.
     */
    public boolean isVanished(OnlineUser onlineUser) {
        if (!config.getBoolean("vanish-protection.enabled", false)) return false;
        if (!plugin.isPlaceholdersAPIConfigured()) return false;

        String placeholder = config.getString("vanish-protection.placeholder", "");
        if (placeholder.isEmpty()) return false;

        String expected = config.getString("vanish-protection.vanish-value", "Yes");
        String actual = PlaceholderAPI.setPlaceholders(onlineUser.getPlayerInstance(), placeholder);
        return expected.equals(actual);
    }

    /**
     * Marks a player as vanished and simulates a logout.
     * Delegates to the internal implementation with fromJoin = false.
     */
    public void addVanishedPlayer(OnlineUser onlineUser, boolean force) {
        addVanishedPlayerInternal(onlineUser, force, false);
    }

    /**
     * Internal vanish logic.
     * @param fromJoin true when the player joined directly in vanish — uses previousSessionLastSeen
     *                 so the join time is not revealed to observers.
     */
    private void addVanishedPlayerInternal(OnlineUser onlineUser, boolean force, boolean fromJoin) {
        if (!force && !isVanished(onlineUser)) return;
        if (vanishedPlayers.contains(onlineUser)) return;

        vanishedPlayers.add(onlineUser);

        long stat = onlineUser.getPlayerInstance().getStatistic(Statistic.PLAY_ONE_MINUTE);
        onlineUser.updateAllOnQuitAsync(stat, () -> {
            onlineUser.syncAfterVanishPersist(stat);

            // For join-in-vanish: override lastSeen so observers don't see the join timestamp.
            if (fromJoin && onlineUser.getPreviousSessionLastSeen() != null) {
                onlineUser.setLastSeenToAsync(onlineUser.getPreviousSessionLastSeen());
            }

            vanishSnapshots.put(onlineUser.getUuid(), DBUser.createFrozenSnapshot(onlineUser));

            // Update cached leaderboard so the live OnlineUser entry is replaced with the snapshot.
            DBUsersManager.getInstance().updateCachedTopPlayers(onlineUser);
            if(config.getBoolean("vanish-protection.debug", false))
                plugin.getLogger().info("[Vanish] Added " + onlineUser.getNickname() + " to vanish "
                        + (fromJoin ? " (joined in vanish)" : ""));
        });
    }

    /**
     * Removes a player from the vanished list and clears their snapshot.
     * Called both on explicit unvanish and on quit — no re-anchoring here since
     * on quit the baselines are no longer needed, and on unvanish the tracking
     * already resumed correctly from the vanish-time re-anchor.
     */
    public void removeVanishedPlayer(OnlineUser onlineUser) {
        vanishedPlayers.remove(onlineUser);
        vanishSnapshots.remove(onlineUser.getUuid());
        if(config.getBoolean("vanish-protection.debug", false))
            plugin.getLogger().info("[Vanish] Removed " + onlineUser.getNickname() + " from vanish");
    }

    public void addOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.put(onlineUser.getNickname().toLowerCase(), onlineUser);
        onlineUsersByUUID.put(onlineUser.getUuid(), onlineUser);

        addVanishedPlayerInternal(onlineUser, false, true);
    }

    public void removeOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.remove(onlineUser.getNickname().toLowerCase());
        onlineUsersByUUID.remove(onlineUser.getUuid());

        removeVanishedPlayer(onlineUser);
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

    private void startVanishPollSchedule() {
        if (!config.getBoolean("vanish-protection.enabled", false)) return;
        if (!plugin.isPlaceholdersAPIConfigured()) return;

        long intervalTicks = config.getInt("vanish-protection.poll-interval-seconds", 5) * 20L;

        vanishPollSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                for (OnlineUser user : new ArrayList<>(onlineUsersByName.values())) {
                    boolean shouldBeVanished = isVanished(user);
                    boolean currentlyVanished = isCurrentlyVanished(user);

                    if (shouldBeVanished && !currentlyVanished) {
                        addVanishedPlayer(user, true);
                        if (config.getBoolean("vanish-protection.debug", false))
                            plugin.getLogger().info("[Vanish] Poll detected vanish for " + user.getNickname());
                    } else if (!shouldBeVanished && currentlyVanished) {
                        removeVanishedPlayer(user);
                        DBUsersManager.getInstance().updateCachedTopPlayers(user);
                        if (config.getBoolean("vanish-protection.debug", false))
                            plugin.getLogger().info("[Vanish] Poll detected unvanish for " + user.getNickname());
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
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
        // Capture vanished UUIDs before going async so the check is thread-safe.
        Set<String> vanishedUUIDs = new HashSet<>();
        for (OnlineUser user : vanishedPlayers) {
            vanishedUUIDs.add(user.getUuid());
        }
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
                    // Playtime and AFK are persisted for all users (crash safety).
                    // lastSeen is skipped for vanished players — it was frozen at vanish time.
                    s.user().updatePlayTimeWithSnapshot(s.snapshot());
                    s.user().updateAFKPlayTimeWithSnapshot(s.snapshot());
                    if (!vanishedUUIDs.contains(s.user().getUuid())) {
                        s.user().updateLastSeen();
                    }
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
        Optional.ofNullable(vanishPollSchedule).ifPresent(BukkitTask::cancel);
    }

    /**
     * Lightweight value type to carry a playtime snapshot alongside its user,
     * avoiding repeated stat reads across thread boundaries.
     */
    private record UserSnapshot(OnlineUser user, long snapshot) {}
}