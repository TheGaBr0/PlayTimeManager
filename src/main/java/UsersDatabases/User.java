package UsersDatabases;

import SQLiteDB.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.UUID;

public class User {

    private final Player p;
    protected final String uuid;
    protected final String nickname;
    protected long fromServerOnJoinPlayTime;
    protected long DBPlayTime;
    protected long actualPlayTime;
    protected final PlayTimeManager plugin =  PlayTimeManager.getInstance();
    protected Database db = plugin.getDatabase();

    public User(Player p){
        this.p = p;
        uuid = p.getUniqueId().toString();
        nickname = p.getName();
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

        userMapping();
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

        //ottengo il playtime registrato nel db
        DBPlayTime = db.getTotalPlaytime(uuid);
    }

    public void manuallyUpdatePlayTime(long playtime){
        db.updateArtificialPlaytime(uuid, db.getArtificialPlaytime(uuid)+playtime);
        updatePlayTime();
    }

    public void updatePlayTime(){
        actualPlayTime =  DBPlayTime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
        db.updatePlaytime(uuid, actualPlayTime);
        actualPlayTime += db.getArtificialPlaytime(uuid);
    }

    public long getPlayTime(){
        updatePlayTime();
        return actualPlayTime;
    }

    public String getUuid(){
        return uuid;
    }

    public String getName(){
        return nickname;
    }

}
