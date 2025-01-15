package Users;

import SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class DBUser {
    protected String uuid;
    protected String nickname;
    protected long DBplaytime;
    protected long artificialPlaytime;
    protected static final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected long fromServerOnJoinPlayTime;
    protected ArrayList<String> completedGoals;
    protected static PlayTimeDatabase db = plugin.getDatabase();

    // Private constructor
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime, ArrayList<String> completedGoals) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.completedGoals = completedGoals;
    }

    public DBUser(Player p) {
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        userMapping();
        this.DBplaytime = db.getPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
        this.completedGoals = db.getCompletedGoals(uuid);
    }

    // Factory method to create DBUser by UUID
    public static DBUser fromUUID(String uuid) {
        String nickname = db.getNickname(uuid);

        if(uuid == null)
            return null;

        long playtime = db.getPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        ArrayList<String> completedGoals = db.getCompletedGoals(uuid);
        return new DBUser(uuid, nickname, playtime, artificialPlaytime, completedGoals);
    }

    // Factory method to create DBUser by nickname
    public static DBUser fromNickname(String nickname) {
        String uuid = db.getUUIDFromNickname(nickname);

        if(uuid == null)
            return null;

        long playtime = db.getPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        ArrayList<String> completedGoals = db.getCompletedGoals(uuid);
        return new DBUser(uuid, nickname, playtime, artificialPlaytime, completedGoals);
    }


    public String getUuid() {
        return uuid;
    }


    public String getNickname() {
        return nickname;
    }

    public long getPlaytime() {
        return DBplaytime + artificialPlaytime;
    }

    public long getArtificialPlaytime() {
        return artificialPlaytime;
    }

    public void setArtificialPlaytime(long artificialPlaytime) {
        this.artificialPlaytime = artificialPlaytime;
        db.updateArtificialPlaytime(uuid, artificialPlaytime);
    }

    public boolean hasCompletedGoal(String goalName){
        return completedGoals.contains(goalName);
    }

    public void markGoalAsCompleted(String goalName){
        completedGoals.add(goalName);
        db.updateCompletedGoals(uuid, completedGoals);
    }

    public void unmarkGoalAsCompleted(String goalName){
        completedGoals.remove(goalName);
        db.updateCompletedGoals(uuid, completedGoals);
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