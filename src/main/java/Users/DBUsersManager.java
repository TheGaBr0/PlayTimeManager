package Users;

import SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.ArrayList;
import java.util.List;

public class DBUsersManager {
    private final PlayTimeDatabase db;
    private final PlayTimeManager plugin;
    private static DBUsersManager instance;

    private DBUsersManager() {
        this.plugin = PlayTimeManager.getInstance();
        this.db = plugin.getDatabase();
    }

    public static DBUsersManager getInstance() {
        if (instance == null) {
            instance = new DBUsersManager();
        }
        return instance;
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