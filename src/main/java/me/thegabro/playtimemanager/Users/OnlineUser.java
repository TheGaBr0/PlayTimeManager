package me.thegabro.playtimemanager.Users;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;

public class OnlineUser extends DBUser {
    private final Player p;

    public OnlineUser(Player p) {
        super(p);
        this.p = p;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    private long getCachedPlayTime() {
        return DBplaytime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    public void updateDB() {
        db.updatePlaytime(uuid, getCachedPlayTime());
    }

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
        db.updateLastSeen(uuid, this.lastSeen);

    }


    @Override
    public long getPlaytime() {
        return getCachedPlayTime() + artificialPlaytime;
    }

    @Override
    public LocalDateTime getLastSeen() {
        return LocalDateTime.now();
    }
}
