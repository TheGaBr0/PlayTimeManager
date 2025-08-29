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

    public OnlineUser(Player p) {
        super(p);
        this.playerInstance = p;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.afkStartPlaytime = 0;
        this.currentSessionAFKTime = 0;
        this.afkTimeFinalized = false;
    }

    private long getCachedPlayTime() {
        return DBplaytime + (playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    public void updatePlayTime() {
        long currentPlaytime = getCachedPlayTime();
        db.updatePlaytime(uuid, currentPlaytime);
    }

    public void updateAFKPlayTime() {
        // Only finalize if not already done
        if (!afkTimeFinalized) {
            finalizeCurrentAFKSession();
        }

        long totalAFKTime = DBAFKplaytime + currentSessionAFKTime;

        db.updateAFKPlaytime(uuid, totalAFKTime);
    }

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
        db.updateLastSeen(uuid, this.lastSeen);
    }

    public Player getPlayerInstance(){
        return playerInstance;
    }

    @Override
    public long getPlaytime() {
        long totalPlaytime = getCachedPlayTime() + artificialPlaytime;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            totalPlaytime -= getAFKPlaytime();
        }

        return Math.max(0, totalPlaytime);
    }

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

    @Override
    public LocalDateTime getLastSeen() {
        return LocalDateTime.now();
    }

    public void refreshFromServerOnJoinPlayTime(){
        this.fromServerOnJoinPlayTime = playerInstance.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    // AFK management methods
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
     * Finalizes AFK time for the current session
     * Should be called when player quits while AFK or when updating AFK playtime
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