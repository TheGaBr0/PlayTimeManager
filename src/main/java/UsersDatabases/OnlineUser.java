package UsersDatabases;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public class OnlineUser extends DBUser{

    private final long fromServerOnJoinPlayTime;
    private long actualPlayTime;
    private Player p;

    public OnlineUser(Player p) {
        super(p);
        this.p = p;
        userMapping();
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

    }

    public void userMapping(){
        //controllo se esiste già questo utente
        if(db.playerExists(uuid)) {
            //controllo se non ha cambiato nickname
            if (!db.getNickname(uuid).equals(nickname)) {
                //se ha cambiato nickname aggiorno i dati sul db
                db.updateNickname(uuid, nickname);
            }
        }else{
            //se non esiste lo aggiungo, come tempo di gioco metto quello registrato dal server
            db.addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }


    public void updatePlayTime(){
        actualPlayTime =  DBplaytime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
        db.updatePlaytime(uuid, actualPlayTime);
        actualPlayTime += db.getArtificialPlaytime(uuid);
    }

    public long getPlayTime(){
        updatePlayTime();
        return actualPlayTime;
    }

}
