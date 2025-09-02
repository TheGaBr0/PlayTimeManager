package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Utils;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;

public class OnlineUser extends DBUser {
    protected final Player playerInstance;
    private long afkStartPlaytime; // Player's PLAY_ONE_MINUTE when AFK started
    private long currentSessionAFKTime; // AFK time accumulated in current session (in ticks)
    private boolean afkTimeFinalized; // Flag to prevent double finalization

    /**
     * Constructs a new OnlineUser instance for an active player.
     * Initializes player statistics and AFK tracking variables.
     *
     * @param p the Player object representing the online player
     */
    public OnlineUser(Player p) {
        super(p);
        this.playerInstance = p;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.afkStartPlaytime = 0;
        this.currentSessionAFKTime = 0;
        this.afkTimeFinalized = false;
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
     * Updates playtime using a specific snapshot value instead of current statistic.
     * This ensures consistency when called from asynchronous contexts after quit events.
     *
     * @param playtimeSnapshot The PLAY_ONE_MINUTE statistic value to use for calculations
     */
    public void updatePlayTimeWithSnapshot(long playtimeSnapshot) {
        long currentPlaytime = DBplaytime + (playtimeSnapshot - fromServerOnJoinPlayTime) + artificialPlaytime;
        db.updatePlaytime(uuid, currentPlaytime);
    }

    /**
     * Updates the player's playtime in the database using current cached playtime.
     * Uses the current playtime statistics from the server.
     */
    public void updatePlayTime() {
        long currentPlaytime = getCachedPlayTime();
        db.updatePlaytime(uuid, currentPlaytime);
    }

    /**
     * Updates the player's AFK playtime in the database.
     * Finalizes the current AFK session if not already done and saves total AFK time.
     */
    public void updateAFKPlayTime() {
        // Only finalize if not already done
        if (!afkTimeFinalized) {
            finalizeCurrentAFKSession();
        }

        long totalAFKTime = DBAFKplaytime + currentSessionAFKTime;

        db.updateAFKPlaytime(uuid, totalAFKTime);
    }

    /**
     * Updates the player's last seen timestamp to the current time.
     * Saves the timestamp to the database.
     */
    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
        db.updateLastSeen(uuid, this.lastSeen);
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
        long totalPlaytime = getCachedPlayTime() + artificialPlaytime;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            totalPlaytime -= getAFKPlaytime();
        }

        return Math.max(0, totalPlaytime);
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
    public LocalDateTime getLastSeen() {
        return LocalDateTime.now();
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
}