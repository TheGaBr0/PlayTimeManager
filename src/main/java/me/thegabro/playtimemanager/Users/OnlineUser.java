package me.thegabro.playtimemanager.Users;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public class OnlineUser extends DBUser {
    protected final Player p;

    public OnlineUser(Player p) {
        super(p);
        this.p = p;
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    private long getCachedPlayTime() {
        return DBplaytime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
    }

    public void updatePlayTime() {
        db.updatePlaytime(uuid, getCachedPlayTime());
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
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;

        // Reset completed goals
        this.completedGoals.clear();

        // Reset rewards
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        // Update all values in database
        db.updatePlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateCompletedGoals(uuid, completedGoals);
        db.updateLastSeen(uuid, this.lastSeen);
        db.updateFirstJoin(uuid, this.firstJoin);
        db.setRelativeJoinStreak(uuid, 0);
        db.setAbsoluteJoinStreak(uuid, 0);
        db.updateReceivedRewards(uuid, receivedRewards);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }
}