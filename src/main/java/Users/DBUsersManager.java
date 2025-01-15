package Users;

import SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.ArrayList;
import java.util.List;

public class DBUsersManager {
    private final PlayTimeDatabase db;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static DBUsersManager instance;
    private final OnlineUsersManager onlineUsersManager;
    private DBUsersManager() {
        this.db = plugin.getDatabase();
        onlineUsersManager = plugin.getOnlineUsersManager();
    }

    public static DBUsersManager getInstance() {
        if (instance == null) {
            instance = new DBUsersManager();
        }
        return instance;
    }

    public DBUser getUserFromNickname(String nickname) {

        DBUser user = onlineUsersManager.getOnlineUser(nickname);
        if(user == null)
            user = DBUser.fromNickname(nickname);

        return user;
    }

    public DBUser getUserFromUUID(String uuid) {

        if(db.playerExists(uuid)){
            DBUser user = onlineUsersManager.getOnlineUserByUUID(uuid);
            if(user == null)
                user = DBUser.fromUUID(uuid);

            return user;
        }

        return null;

    }

    public void removeGoalFromAllUsers(String goalName) {
        List<String> allNicknames = db.getAllNicknames();

        for (String nickname : allNicknames) {
            DBUser user = DBUser.fromNickname(nickname);
            if (user.hasCompletedGoal(goalName)) {
                user.unmarkGoalAsCompleted(goalName);
            }
        }
    }

    public ArrayList<DBUser> getAllDBUsers(){
        ArrayList<DBUser> dbUsers = new ArrayList<>();

        for(String nickname : db.getAllNicknames()){
            dbUsers.add(DBUser.fromNickname(nickname));
        }

        return dbUsers;
    }
}