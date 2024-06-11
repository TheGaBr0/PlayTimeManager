package UsersDatabases;

import SQLiteDB.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class UsersManager {
    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final ArrayList<User> onlineUsers = new ArrayList<>();
    private final Database db = plugin.getDatabase();

    public UsersManager(){
        loadOnlineUsers();
    }

    public boolean userExists(String nickname) {

        return  db.getUUIDFromNickname(nickname) != null;

    }


    public long getPlayTimeByNick(String nickname){
        for(User u : onlineUsers){
            if(u.getName().equals(nickname)){
                return u.getPlayTime();
            }
        }

        return db.getTotalPlaytime(db.getUUIDFromNickname(nickname));
    }

    public void setArtificialPlayTimeByNick(String nickname, long playtime){
        db.updateArtificialPlaytime(db.getUUIDFromNickname(nickname), playtime);
    }

    public long getArtificialPlayTimeByNick(String nickname){
        return db.getArtificialPlaytime(db.getUUIDFromNickname(nickname));
    }

    public User getUserByNickname(String nickname){
        for(User u : onlineUsers){
            if(u.getName().equals(nickname)){
                return u;
            }
        }
        return null;
    }


    public void addOnlineUser(User user){
        onlineUsers.add(user);
    }

    public void removeOnlineUser(User user){
        onlineUsers.remove(user);
    }

    public void loadOnlineUsers(){
        for(Player p : Bukkit.getOnlinePlayers()){
            User user = new User(p);
            onlineUsers.add(user);
        }
    }

    public List<String> getStoredPlayers() {
        return db.getAllNicknames();
    }

    public String convertTime(long secondsx) {
        int days = (int) TimeUnit.SECONDS.toDays(secondsx);
        int hours = (int) (TimeUnit.SECONDS.toHours(secondsx) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(secondsx) - TimeUnit.HOURS.toMinutes(hours)
                - TimeUnit.DAYS.toMinutes(days));
        int seconds = (int) (TimeUnit.SECONDS.toSeconds(secondsx) - TimeUnit.MINUTES.toSeconds(minutes)
                - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days != 0) {
            return days + "d, " + hours + "h, " + minutes + "m, " + seconds + "s";
        } else {
            if (hours != 0) {
                return hours + "h, " + minutes + "m, " + seconds + "s";
            } else {
                if (minutes != 0) {
                    return minutes + "m, " + seconds + "s";
                } else {
                    return seconds + "s";
                }
            }

        }
    }

}
