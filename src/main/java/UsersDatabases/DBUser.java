package UsersDatabases;

import SQLiteDB.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public class DBUser {
    protected String uuid;
    protected String nickname;
    protected long DBplaytime;
    protected long artificialPlaytime;
    protected static final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected long fromServerOnJoinPlayTime;
    protected static Database db = plugin.getDatabase();

    // Private constructor
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
    }

    public DBUser(Player p) {
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        userMapping();
        this.DBplaytime = db.getTotalPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
    }

    // Factory method to create DBUser by UUID
    public static DBUser fromUUID(String uuid) {
        String nickname = db.getNickname(uuid);
        long playtime = db.getTotalPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        return new DBUser(uuid, nickname, playtime, artificialPlaytime);
    }

    // Factory method to create DBUser by nickname
    public static DBUser fromNickname(String nickname) {
        String uuid = db.getUUIDFromNickname(nickname);
        long playtime = db.getTotalPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        return new DBUser(uuid, nickname, playtime, artificialPlaytime);
    }


    public String getUuid() {
        return uuid;
    }


    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        db.updateNickname(uuid, nickname);
        this.nickname = nickname;
    }

    public long getPlaytime() {
        return DBplaytime;
    }


    public long getArtificialPlaytime() {
        return artificialPlaytime;
    }

    public void setArtificialPlaytime(long artificialPlaytime) {
        this.artificialPlaytime = artificialPlaytime;
        db.updateArtificialPlaytime(uuid, artificialPlaytime);
    }

    private void userMapping(){
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
}