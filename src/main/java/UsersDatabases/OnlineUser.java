package UsersDatabases;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public class OnlineUser extends DBUser{

    private long actualPlayTime;
    private Player p;

    public OnlineUser(Player p) {
        super(p);
        this.p = p;
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

    }


    public void updatePlayTime(){
        actualPlayTime =  DBplaytime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
        db.updatePlaytime(uuid, actualPlayTime);
        actualPlayTime += db.getArtificialPlaytime(uuid);
    }

    public long getPlaytime(){
        updatePlayTime();
        return actualPlayTime;
    }

}
