package UsersDatabases;

import Main.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.UUID;

public class User {

    private Player p;
    protected final String uuid;
    protected final String nickname;
    protected long fromServerOnJoinPlayTime;
    protected long DBPlayTime;
    protected long actualPlayTime;
    protected final PlayTimeManager plugin =  PlayTimeManager.getInstance();
    protected PlayTimeDB playTimeDB;
    protected UuidDB uuidDB;
    protected CustomPlayTimeDB customPlayTimeDB;

    public User(Player p){
        playTimeDB = plugin.getPlayTimeDB();
        uuidDB = plugin.getUuidDB();
        customPlayTimeDB = plugin.getCustomPlayTImeDB();
        this.p = p;
        uuid = p.getUniqueId().toString();
        nickname = p.getName();
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

        userMapping();
    }

    public User(String uuid){
        playTimeDB = plugin.getPlayTimeDB();
        uuidDB = plugin.getUuidDB();
        customPlayTimeDB = plugin.getCustomPlayTImeDB();
        if(uuid.equals("Unknown")){
            this.uuid = "";
            nickname = "";
            DBPlayTime = 0L;
        }else{
            this.uuid = uuid;

            if(uuidDB.getPlayerName(uuid) == null){
                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

                if(p.hasPlayedBefore()){
                    nickname = p.getName();
                    uuidDB.addPlayerToDatabase(uuid, nickname);
                }
                else
                    nickname = "";

            }else{
                nickname = uuidDB.getPlayerName(uuid);
            }
            DBPlayTime = playTimeDB.getPlaytimeForUUID(uuid);
        }
    }

    public void userMapping(){
        //controllo se esiste già questo utente
        if(uuidDB.checkUUID(uuid)) {
            //controllo se non ha cambiato nickname
            if (!uuidDB.checkForUuidNicknamePairings(uuid, nickname)) {
                //se ha cambiato nickname aggiorno i dati sul db
                uuidDB.replaceUuidData(uuid, nickname);
            }
        }else{
            //se non esiste lo aggiungo al database
            uuidDB.addPlayerToDatabase(uuid, nickname);
        }

        //controllo se esiste già questo utente
        if(!playTimeDB.checkUUID(uuid)){
            //se non esiste lo aggiungo, come tempo di gioco metto quello registrato dal server
            playTimeDB.updatePlayerToDatabase(uuid, fromServerOnJoinPlayTime);
        }

        //ottengo il playtime registrato nel db
        DBPlayTime = playTimeDB.getPlaytimeForUUID(uuid);
    }

    public void updateDBPlayTime(long playtime){
        DBPlayTime = playtime;
    }

    public void manuallyUpdatePlayTime(long playtime){
        customPlayTimeDB.updatePlayerToDatabase(uuid, playtime);
        updatePlayTime();
    }

    public void updatePlayTime(){
        actualPlayTime =  DBPlayTime + (p.getStatistic(Statistic.PLAY_ONE_MINUTE) - fromServerOnJoinPlayTime);
        playTimeDB.updatePlayerToDatabase(uuid, actualPlayTime);
        actualPlayTime += customPlayTimeDB.getCustomPlayTime(uuid);
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
