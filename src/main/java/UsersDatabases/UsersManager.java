package UsersDatabases;

import Main.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Objects;

public class UsersManager {
    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final ArrayList<User> onlineUsers = new ArrayList<>();
    protected final PlayTimeDB playTimeDB;
    protected final UuidDB uuidDB;

    public UsersManager(){
        playTimeDB = plugin.getPlayTimeDB();
        uuidDB = plugin.getUuidDB();
        loadOnlineUsers();
    }

    public User getUserByNickname(String nickname) {

        for (User user:onlineUsers) {
            if(user.getName().equals(nickname))
                return user;
        }

        String uuid = uuidDB.getUuid(nickname);
        return new OfflineUser(Objects.requireNonNullElse(uuid, "Unknown"));
    }

    public User getUserByUuid(String uuid) {

        for (User user:onlineUsers) {
            if(user.getUuid().equals(uuid))
                return user;
        }

        return new OfflineUser(uuid);
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

    public ArrayList<User> getStoredPlayers(){

        ArrayList<User> users = new ArrayList<>();

        for(String uuid : playTimeDB.getKeySet())
            users.add(getUserByUuid(uuid));

        return users;
    }

}
