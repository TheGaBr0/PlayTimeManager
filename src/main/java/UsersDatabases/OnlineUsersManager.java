package UsersDatabases;

import SQLiteDB.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class OnlineUsersManager {
    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final ArrayList<OnlineUser> onlineUsers = new ArrayList<>();
    private final Database db = plugin.getDatabase();

    public OnlineUsersManager(){
        loadOnlineUsers();
    }

    public boolean userExists(String nickname) {
        return  db.getUUIDFromNickname(nickname) != null;
    }

    public void addOnlineUser(OnlineUser onlineUser){
        onlineUsers.add(onlineUser);
    }

    public void removeOnlineUser(OnlineUser onlineUser){
        onlineUsers.remove(onlineUser);
    }

    public void loadOnlineUsers(){
        for(Player p : Bukkit.getOnlinePlayers()){
            OnlineUser onlineUser = new OnlineUser(p);
            onlineUsers.add(onlineUser);
        }
    }

    public OnlineUser getOnlineUser(String nickname){
        for(OnlineUser user : onlineUsers){
            if(user.getNickname().equals(nickname))
                return user;
        }
        return null;
    }

}
