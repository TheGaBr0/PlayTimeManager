package me.thegabro.playtimemanager.Users;

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
     * Private constructor - use createAsync instead.
     */
    private OnlineUser(Player p) {
        super(); // Call protected no-arg constructor
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
     * @param p the Player object representing the online player
     * @param callback Called when user is fully loaded with the OnlineUser instance
     */
    public static void createOnlineUserAsync(Player p, Consumer<OnlineUser> callback) {
        OnlineUser user = new OnlineUser(p);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Perform user mapping and load data
            user.userMappingSync();
            user.loadUserDataSync();

            // Handle legacy null first_join values
            if(user.firstJoin == null){
                user.firstJoin = Instant.now();
                db.getPlayerDAO().updateFirstJoin(user.uuid, user.firstJoin);
            }

            // Return to main thread with loaded user
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(user));
        });
    }

    /**
     * Synchronous version of user mapping for internal use.
     * Must be called from async context.
     */
    private void userMappingSync() {
        boolean uuidExists = db.getPlayerDAO().playerExists(uuid);
        String existingNickname = uuidExists ? db.getPlayerDAO().getNickname(uuid) : null;
        String existingUUID = db.getPlayerDAO().getUUIDFromNickname(nickname);

        if (uuidExists) {
            if (!nickname.equals(existingNickname)) {
                db.getPlayerDAO().updateNickname(uuid, nickname);
            }
        } else if (existingUUID != null) {
            db.getPlayerDAO().updateUUID(uuid, nickname);
        } else {
            db.getPlayerDAO().addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }

    /**
     * Calculates and returns the cached playtime for the player.
     * Combines database playtime with the current session playtime.
     *
     * @return the total cached playtime in ticks
     */
    private long getCachedPlayTime() {
        return DBplaytime + (playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    /**
     * Updates playtime asynchronously using a specific snapshot value.
     * This ensures consistency when called from asynchronous contexts after quit events.
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic value to use for calculations
     * @param callback Optional callback to run on main thread after update completes
     */
    public void updatePlayTimeWithSnapshotAsync(long playtimeSnapshot, Runnable callback) {
        long currentPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version - use updatePlayTimeWithSnapshotAsync when possible
     */
    public void updatePlayTimeWithSnapshot(long playtimeSnapshot) {
        updatePlayTimeWithSnapshotAsync(playtimeSnapshot, null);
    }

    /**
     * Updates the player's playtime in the database asynchronously using current cached playtime.
     * Uses the current playtime statistics from the server.
     *
     * @param callback Optional callback to run on main thread after update completes
     */
    public void updatePlayTimeAsync(Runnable callback) {
        long currentPlaytime = getCachedPlayTime();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version - use updatePlayTimeAsync when possible
     */
    public void updatePlayTime() {
        updatePlayTimeAsync(null);
    }

    /**
     * Updates the player's AFK playtime in the database asynchronously.
     *
     * @param callback Optional callback to run on main thread after update completes
     */
    public void updateAFKPlayTimeAsync(Runnable callback) {
        long totalAFKTime = getAFKPlaytime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version - use updateAFKPlayTimeAsync when possible
     */
    public void updateAFKPlayTime() {
        updateAFKPlayTimeAsync(null);
    }

    /**
     * Updates the player's AFK playtime using a snapshot value.
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic value to use
     * @param callback Optional callback to run on main thread after update completes
     */
    public void updateAFKPlayTimeWithSnapshotAsync(long playtimeSnapshot, Runnable callback) {
        long totalAFKTime = getAFKPlaytimeWithSnapshot(playtimeSnapshot);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version with snapshot
     */
    public void updateAFKPlayTimeWithSnapshot(long playtimeSnapshot) {
        updateAFKPlayTimeWithSnapshotAsync(playtimeSnapshot, null);
    }

    /**
     * Updates the player's last seen timestamp to the current time asynchronously.
     * Saves the timestamp to the database.
     *
     * @param callback Optional callback to run on main thread after update completes
     */
    public void updateLastSeenAsync(Runnable callback) {
        this.lastSeen = Instant.now();
        final Instant timestampToSave = this.lastSeen;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updateLastSeen(uuid, timestampToSave);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version - use updateLastSeenAsync when possible
     */
    public void updateLastSeen() {
        updateLastSeenAsync(null);
    }

    /**
     * Returns the Bukkit Player instance associated with this online user.
     *
     * @return the Player instance
     */
    public Player getPlayerInstance(){
        return playerInstance;
    }

    /**
     * Calculates and returns the total playtime for the player.
     * Includes cached playtime, artificial playtime, and optionally excludes AFK time.
     *
     * @return the total playtime in ticks, minimum 0
     */
    @Override
    public long getPlaytime() {
        long cachedPlaytime = getCachedPlayTime();
        long totalPlaytime = cachedPlaytime + artificialPlaytime;
        long afkPlaytime = 0;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            afkPlaytime = getAFKPlaytime();
            totalPlaytime -= afkPlaytime;
        }

        long result = Math.max(0, totalPlaytime);
        return result;
    }

    /**
     * Gets playtime using a specific snapshot value for consistent calculations
     * Overrides the base DBUser implementation for online users
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic snapshot to use
     * @return Total playtime in ticks
     */
    @Override
    public long getPlaytimeWithSnapshot(long playtimeSnapshot) {
        long totalPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime) + artificialPlaytime;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            totalPlaytime -= getAFKPlaytimeWithSnapshot(playtimeSnapshot);
        }

        return Math.max(0, totalPlaytime);
    }

    /**
     * Calculates and returns the total AFK playtime for the player.
     * Includes database AFK time, current session AFK time, and ongoing AFK time if applicable.
     *
     * @return the total AFK playtime in ticks, minimum 0
     */
    @Override
    public long getAFKPlaytime() {
        long totalAFK = DBAFKplaytime + currentSessionAFKTime;

        // If currently AFK and not finalized, add ongoing AFK time
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            long currentPlaytime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long ongoingAFKTime = currentPlaytime - afkStartPlaytime;
            totalAFK += ongoingAFKTime;
        }
        return Math.max(0, totalAFK);
    }

    /**
     * Gets AFK playtime using a specific snapshot value for consistent calculations
     * Overrides the base DBUser implementation for online users
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic snapshot to use
     * @return Total AFK playtime in ticks
     */
    @Override
    public long getAFKPlaytimeWithSnapshot(long playtimeSnapshot) {
        long totalAFK = DBAFKplaytime + currentSessionAFKTime;

        // If currently AFK and not finalized, add ongoing AFK time using snapshot
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            long ongoingAFKTime = playtimeSnapshot - afkStartPlaytime;
            totalAFK += ongoingAFKTime;
        }

        return Math.max(0, totalAFK);
    }

    /**
     * Returns the current time as the last seen timestamp for online players.
     * Online players are always considered "just seen".
     *
     * @return the current LocalDateTime
     */
    @Override
    public Instant getLastSeen() {
        return Instant.now();
    }

    /**
     * Refreshes the server join playtime statistic from the current player instance.
     * Used to reset the baseline for playtime calculations.
     */
    public void refreshFromServerOnJoinPlayTime(){
        this.fromServerOnJoinPlayTime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    /**
     * Sets the player's AFK status and manages AFK time tracking.
     * Records start/end times and calculates AFK duration when status changes.
     *
     * @param isAFK true if the player should be marked as AFK, false otherwise
     */
    public void setAFK(boolean isAFK) {

        if (isAFK && !this.afk) {
            // Player is going AFK - record current playtime
            this.afk = true;
            this.afkStartPlaytime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
            this.afkTimeFinalized = false;
        } else if (!isAFK && this.afk) {
            // Player is no longer AFK - calculate AFK duration
            if (this.afkStartPlaytime > 0 && !afkTimeFinalized) {
                long currentPlaytime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
                long afkDuration = currentPlaytime - this.afkStartPlaytime;
                this.currentSessionAFKTime += afkDuration;

            }
            this.afk = false;
            this.afkStartPlaytime = 0;
            this.afkTimeFinalized = false;
        }
    }

    /**
     * Finalizes AFK time for the current session using a specific playtime snapshot.
     * This prevents timing inconsistencies between synchronous and asynchronous operations.
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic value to use for calculations
     */
    public void finalizeCurrentAFKSession(long playtimeSnapshot) {
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            long afkDuration = playtimeSnapshot - afkStartPlaytime;
            currentSessionAFKTime += afkDuration;
            afkTimeFinalized = true;
        }
    }

    /**
     * Finalizes AFK time for the current session using current player statistics.
     * Should be called when player quits while AFK or when updating AFK playtime.
     */
    public void finalizeCurrentAFKSession() {
        if (afk && afkStartPlaytime > 0 && !afkTimeFinalized) {
            long currentPlaytime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long afkDuration = currentPlaytime - afkStartPlaytime;
            currentSessionAFKTime += afkDuration;
            afkTimeFinalized = true;
        }
    }

    /**
     * Checks if a specific reward subinstance is expired.
     *
     * @param subInstance The reward subinstance to check
     * @return true if the reward is expired, false otherwise
     */
    public boolean isExpired(RewardSubInstance subInstance){
        for(RewardSubInstance subInstance2 : rewardsToBeClaimed){
            if(Objects.equals(subInstance2.mainInstanceID(), subInstance.mainInstanceID()) &&
                    Objects.equals(subInstance2.requiredJoins(), subInstance.requiredJoins()))
                return subInstance2.expired();
        }
        return false;
    }

    /**
     * Comprehensive async update for all player data on quit.
     * Updates playtime, AFK time, and last seen in a single operation.
     * Uses snapshot values for consistency.
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic snapshot
     * @param callback Optional callback to run after all updates complete
     */
    public void updateAllOnQuitAsync(long playtimeSnapshot, Runnable callback) {
        // Finalize AFK session if needed
        if (!afkTimeFinalized) {
            finalizeCurrentAFKSession(playtimeSnapshot);
        }

        // Calculate values
        long currentPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime);
        long totalAFKTime = DBAFKplaytime + currentSessionAFKTime;
        Instant lastSeenTime = Instant.now();
        this.lastSeen = lastSeenTime;

        // Perform all DB updates asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.getPlayerDAO().updatePlaytime(uuid, currentPlaytime);
            db.getPlayerDAO().updateAFKPlaytime(uuid, totalAFKTime);
            db.getPlayerDAO().updateLastSeen(uuid, lastSeenTime);

            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    /**
     * Synchronous version - use updateAllOnQuitAsync when possible
     */
    public void updateAllOnQuit(long playtimeSnapshot) {
        updateAllOnQuitAsync(playtimeSnapshot, null);
    }
}