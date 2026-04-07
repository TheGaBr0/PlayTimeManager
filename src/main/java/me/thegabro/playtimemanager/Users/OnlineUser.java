package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

public class OnlineUser extends DBUser {
    protected final Player playerInstance;
    private long afkStartPlaytime;
    private long currentSessionAFKTime;
    private boolean afkTimeFinalized;

    /**
     * Private constructor - use {@link #createOnlineUserAsync} instead.
     */
    private OnlineUser(Player p) {
        super();
        this.playerInstance = p;
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.afkStartPlaytime = 0;
        this.currentSessionAFKTime = 0;
        this.afkTimeFinalized = false;
    }

    /**
     * Asynchronously creates a new OnlineUser instance for an active player.
     * Handles user mapping, loads existing data from database, and initializes AFK tracking.
     *
     * @param p        the Player object representing the online player
     * @param callback called on the main thread when the user is fully loaded
     */
    public static void createOnlineUserAsync(Player p, Consumer<OnlineUser> callback) {
        OnlineUser user = new OnlineUser(p);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.userMappingSync();
            user.loadUserDataSync();

            // Handle legacy records where first_join was never set
            if (user.firstJoin == null) {
                user.firstJoin = Instant.now();
                DatabaseHandler.getInstance().getPlayerDAO().updateFirstJoin(user.uuid, user.firstJoin);
            }

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(user));
        });
    }

    /**
     * Synchronous user-mapping step: ensures UUID/nickname records are consistent.
     * Must be called from an async context.
     */
    private void userMappingSync() {
        boolean uuidExists = DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid);
        String existingNickname = uuidExists
                ? DatabaseHandler.getInstance().getPlayerDAO().getNickname(uuid)
                : null;
        String existingUUID = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);

        DBUsersManager dbUsersManager = DBUsersManager.getInstance();

        if (uuidExists) {
            if (!nickname.equals(existingNickname)) {
                DatabaseHandler.getInstance().getPlayerDAO().updateNickname(uuid, nickname);
                // Cache update must happen on the main thread
                Bukkit.getScheduler().runTask(plugin, () ->
                        dbUsersManager.updateNicknameInCache(uuid, existingNickname, nickname));
            }
        } else if (existingUUID != null) {
            DatabaseHandler.getInstance().getPlayerDAO().updateUUID(uuid, nickname);
            Bukkit.getScheduler().runTask(plugin, () ->
                    dbUsersManager.updateUUIDInCache(existingUUID, uuid, nickname));
        } else {
            DatabaseHandler.getInstance().getPlayerDAO().addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }

    // -------------------------------------------------------------------------
    // Playtime
    // -------------------------------------------------------------------------

    /**
     * Returns the total playtime for this session by combining the persisted DB value
     * with ticks accumulated since the player joined.
     */
    private long getCachedPlayTime() {
        return DBplaytime + (playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    /**
     * Persists playtime using a pre-captured statistic snapshot.
     * Prefer this over {@link #updatePlayTimeAsync} when called after quit events,
     * because the player object may no longer be reliable at that point.
     */
    public void updatePlayTimeWithSnapshotAsync(long playtimeSnapshot, Runnable callback) {
        long currentPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void updatePlayTimeWithSnapshot(long playtimeSnapshot) {
        updatePlayTimeWithSnapshotAsync(playtimeSnapshot, null);
    }

    /**
     * Persists playtime using the live statistic from the player object.
     * Must only be called while the player is still connected and the stat is valid.
     */
    public void updatePlayTimeAsync(Runnable callback) {
        long currentPlaytime = getCachedPlayTime();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void updatePlayTime() {
        updatePlayTimeAsync(null);
    }

    @Override
    public long getPlaytime() {
        long totalPlaytime = getCachedPlayTime() + artificialPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time", false)) {
            totalPlaytime -= getAFKPlaytime();
        }
        return Math.max(0, totalPlaytime);
    }

    @Override
    public long getPlaytimeWithSnapshot(long playtimeSnapshot) {
        long totalPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime) + artificialPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time", false)) {
            totalPlaytime -= getAFKPlaytimeWithSnapshot(playtimeSnapshot);
        }
        return Math.max(0, totalPlaytime);
    }

    // -------------------------------------------------------------------------
    // AFK playtime
    // -------------------------------------------------------------------------

    public void updateAFKPlayTimeAsync(Runnable callback) {
        long totalAFKTime = getAFKPlaytime();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void updateAFKPlayTime() {
        updateAFKPlayTimeAsync(null);
    }

    public void updateAFKPlayTimeWithSnapshotAsync(long playtimeSnapshot, Runnable callback) {
        long totalAFKTime = getAFKPlaytimeWithSnapshot(playtimeSnapshot);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void updateAFKPlayTimeWithSnapshot(long playtimeSnapshot) {
        updateAFKPlayTimeWithSnapshotAsync(playtimeSnapshot, null);
    }

    @Override
    public long getAFKPlaytime() {
        long totalAFK = DBAFKplaytime + currentSessionAFKTime;
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            totalAFK += playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - afkStartPlaytime;
        }
        return Math.max(0, totalAFK);
    }

    @Override
    public long getAFKPlaytimeWithSnapshot(long playtimeSnapshot) {
        long totalAFK = DBAFKplaytime + currentSessionAFKTime;
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            totalAFK += playtimeSnapshot - afkStartPlaytime;
        }
        return Math.max(0, totalAFK);
    }

    /**
     * Sets the player's AFK status and manages AFK time tracking.
     * Records start/end times and calculates AFK duration when status changes.
     */
    @Override
    public void setAFK(boolean isAFK) {
        if (isAFK && !this.afk) {
            this.afk = true;
            this.afkStartPlaytime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
            this.afkTimeFinalized = false;
        } else if (!isAFK && this.afk) {
            if (this.afkStartPlaytime > 0 && !afkTimeFinalized) {
                long afkDuration = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - this.afkStartPlaytime;
                this.currentSessionAFKTime += afkDuration;
            }
            this.afk = false;
            this.afkStartPlaytime = 0;
            this.afkTimeFinalized = false;
        }
    }

    /**
     * Finalizes AFK time using a snapshot value, preventing timing inconsistencies
     * between synchronous and asynchronous operations (e.g. on quit).
     */
    public void finalizeCurrentAFKSession(long playtimeSnapshot) {
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            currentSessionAFKTime += playtimeSnapshot - afkStartPlaytime;
            afkTimeFinalized = true;
        }
    }

    /**
     * Finalizes AFK time using the current live statistic.
     * Use when the player quits while AFK and no snapshot is available.
     */
    public void finalizeCurrentAFKSession() {
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            currentSessionAFKTime += playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - afkStartPlaytime;
            afkTimeFinalized = true;
        }
    }

    // -------------------------------------------------------------------------
    // Last seen
    // -------------------------------------------------------------------------

    public void updateLastSeenAsync(Runnable callback) {
        this.lastSeen = Instant.now();
        final Instant timestampToSave = this.lastSeen;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateLastSeen(uuid, timestampToSave);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void updateLastSeen() {
        updateLastSeenAsync(null);
    }

    /**
     * Always returns the current instant for online users, since they are active right now.
     */
    @Override
    public Instant getLastSeen() {
        return Instant.now();
    }

    /**
     * Returns the last-seen timestamp loaded from the database on login (i.e. from the
     * previous session). Use this instead of {@link #getLastSeen()} when measuring absence
     * duration, e.g. for streak eligibility checks.
     */
    public Instant getPreviousSessionLastSeen() {
        return previousSessionLastSeen;
    }

    // -------------------------------------------------------------------------
    // Player instance
    // -------------------------------------------------------------------------

    /**
     * Returns the Bukkit {@link Player} instance for this online user.
     * Shadows {@link DBUser#getPlayerInstance()} with the stronger typed return.
     */
    @Override
    public Player getPlayerInstance() {
        return playerInstance;
    }

    // -------------------------------------------------------------------------
    // Reset overrides
    // -------------------------------------------------------------------------

    /**
     * Resets all player data and re-anchors the server playtime baseline.
     * Overrides {@link DBUser#resetAsync} because the base implementation sets
     * {@code fromServerOnJoinPlayTime} to 0, which would corrupt {@link #getCachedPlayTime()}
     * for an online user.
     */
    @Override
    public void resetAsync(Runnable callback) {
        super.resetAsync(callback);
        refreshFromServerOnJoinPlayTime();
    }

    /**
     * Resets playtime data and re-anchors the server playtime baseline.
     * Same reasoning as {@link #resetAsync}.
     */
    @Override
    public void resetPlaytimeAsync(Runnable callback) {
        super.resetPlaytimeAsync(callback);
        refreshFromServerOnJoinPlayTime();
    }

    /**
     * Re-anchors {@code fromServerOnJoinPlayTime} to the current server statistic.
     * Call this after any operation that zeroes the persisted playtime, so that
     * subsequent delta calculations start from the correct baseline.
     */
    public void refreshFromServerOnJoinPlayTime() {
        this.fromServerOnJoinPlayTime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    // -------------------------------------------------------------------------
    // Quit persistence
    // -------------------------------------------------------------------------

    /**
     * Persists playtime, AFK time, and last-seen in a single operation using a snapshot.
     * Using a snapshot ensures all three values are consistent with one another even if
     * the player disconnects between the reads.
     *
     * @param playtimeSnapshot the {@code PLAY_ONE_MINUTE} statistic captured before quit
     * @param async            {@code true} to dispatch the DB writes on a background thread;
     *                         {@code false} to run them on the calling thread (blocking)
     * @param callback         optional runnable fired on the main thread after writes complete
     */
    private void updateAllOnQuitInternal(long playtimeSnapshot, boolean async, Runnable callback) {
        if (!afkTimeFinalized) {
            finalizeCurrentAFKSession(playtimeSnapshot);
        }

        long currentPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime);
        long totalAFKTime = DBAFKplaytime + currentSessionAFKTime;
        Instant lastSeenTime = Instant.now();
        this.lastSeen = lastSeenTime;

        Runnable dbTask = () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            DatabaseHandler.getInstance().getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            DatabaseHandler.getInstance().getPlayerDAO().updateLastSeen(uuid, lastSeenTime);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        };

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, dbTask);
        } else {
            dbTask.run(); // BLOCKING — only use when the server is shutting down
        }
    }

    public void updateAllOnQuitAsync(long playtimeSnapshot, Runnable callback) {
        updateAllOnQuitInternal(playtimeSnapshot, true, callback);
    }

    public void updateAllOnQuitSync(long playtimeSnapshot) {
        updateAllOnQuitInternal(playtimeSnapshot, false, null);
    }

    // -------------------------------------------------------------------------
    // Rewards
    // -------------------------------------------------------------------------

    /**
     * Checks whether a specific reward sub-instance is marked as expired.
     */
    public boolean isRewardExpired(RewardSubInstance subInstance) {
        for (RewardSubInstance r : rewardsToBeClaimed) {
            if (Objects.equals(r.mainInstanceID(), subInstance.mainInstanceID()) &&
                    Objects.equals(r.requiredJoins(), subInstance.requiredJoins())) {
                return r.expired();
            }
        }
        return false;
    }
}