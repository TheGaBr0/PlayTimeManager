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

    public void refreshFromServerOnJoinPlayTime(){
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

    }

    @Override
    public void reset() {
        this.DBplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.lastSeen = null;
        this.firstJoin = null;

        // Reset completed goals
        this.completedGoals.clear();

        // Update all values in database
        db.updatePlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateCompletedGoals(uuid, completedGoals);
        db.updateLastSeen(uuid, this.lastSeen);
        db.updateFirstJoin(uuid, this.firstJoin);
    }
}
