package Users;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

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

    @Override
    public long getPlaytime() {
        return getCachedPlayTime() + artificialPlaytime;
    }
}
