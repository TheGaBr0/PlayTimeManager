package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;

public class OnlineUser extends DBUser {
    protected final Player p;
    private long afkStartTime; // Timestamp when AFK started (in ticks)
    private long currentSessionAFKTime; // AFK time accumulated in current session (in ticks)

    public OnlineUser(Player p) {
        super(p);
        this.p = p;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.afkStartTime = 0;
        this.currentSessionAFKTime = 0;
    }

    private long getCachedPlayTime() {
        return DBplaytime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    public void updatePlayTime() {
        db.updatePlaytime(uuid, getCachedPlayTime());
    }

    public void updateAFKPlayTime() {
        long totalAFKTime = getAFKPlaytime();
        db.updateAFKPlaytime(uuid, totalAFKTime);
    }

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
        db.updateLastSeen(uuid, this.lastSeen);
    }

    public Player getPlayer(){
        return p;
    }

    @Override
    public long getPlaytime() {
        long totalPlaytime = getCachedPlayTime() + artificialPlaytime;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            totalPlaytime -= getAFKPlaytime();
        }

        return totalPlaytime;
    }

    @Override
    public long getAFKPlaytime() {
        long totalAFK = DBAFKplaytime + currentSessionAFKTime;

        // If currently AFK, add the time since AFK started
        if (afk && afkStartTime > 0) {
            long ongoingAFKTime = getCurrentServerTicks() - afkStartTime;
            totalAFK += ongoingAFKTime;
        }

        return totalAFK;
    }

    @Override
    public LocalDateTime getLastSeen() {
        return LocalDateTime.now();
    }

    public void refreshFromServerOnJoinPlayTime(){
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    // Helper method to get current server time in ticks
    private long getCurrentServerTicks() {
        // Convert milliseconds to ticks (20 ticks = 1 second = 1000ms)
        return System.currentTimeMillis() / 50; // 1000ms / 20 ticks = 50ms per tick
    }

    // AFK management methods
    public void setAFK(boolean isAFK) {
        if (isAFK && !this.afk) {
            // Player is going AFK
            this.afk = true;
            this.afkStartTime = getCurrentServerTicks();
        } else if (!isAFK && this.afk) {
            // Player is no longer AFK
            this.afk = false;
            if (this.afkStartTime > 0) {
                // Calculate AFK time and add to current session total
                long afkDuration = getCurrentServerTicks() - this.afkStartTime;
                this.currentSessionAFKTime += afkDuration;
                this.afkStartTime = 0;
            }
        }
    }
}