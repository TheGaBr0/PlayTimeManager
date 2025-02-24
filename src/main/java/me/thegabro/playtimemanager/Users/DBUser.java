package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
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
    protected LocalDateTime lastSeen;
    protected LocalDateTime firstJoin;
    protected final GoalsManager goalsManager = GoalsManager.getInstance();

    // Private constructor
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime,
                   ArrayList<String> completedGoals, LocalDateTime lastSeen, LocalDateTime firstJoin) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.completedGoals = completedGoals;
        this.lastSeen = lastSeen;
        this.firstJoin = firstJoin;
    }

    public DBUser(Player p) {
        fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        userMapping();
        this.DBplaytime = db.getPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
        this.completedGoals = db.getCompletedGoals(uuid);
        fixGhostGoals();
        this.lastSeen = db.getLastSeen(uuid);
        this.firstJoin = db.getFirstJoin(uuid);
        if(firstJoin == null){
            firstJoin = LocalDateTime.now();
            db.updateFirstJoin(uuid, firstJoin);
        }
    }

    // Factory method to create DBUser by UUID
    protected static DBUser fromUUID(String uuid) {
        String nickname = db.getNickname(uuid);

        if(uuid == null)
            return null;

        long playtime = db.getPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        ArrayList<String> completedGoals = db.getCompletedGoals(uuid);
        LocalDateTime lastSeen = db.getLastSeen(uuid);
        LocalDateTime firstJoin = db.getFirstJoin(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, completedGoals, lastSeen, firstJoin);
    }

    public void reset() {
        // Reset playtime statistics
        this.DBplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;
        this.lastSeen = null;
        this.firstJoin = null;


        // Reset completed goals
        this.completedGoals.clear();

        // Update all values in database
        db.updatePlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateCompletedGoals(uuid, completedGoals);
        db.updateLastSeen(uuid, this.lastSeen);
        db.updateFirstJoin(uuid, this.firstJoin);
    }

    public LocalDateTime getFirstJoin(){ return firstJoin; }

    public LocalDateTime getLastSeen() { return lastSeen; }

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

    public ArrayList<String> getCompletedGoals(){
        return completedGoals;
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


    // Ensures that every goal in the database is loaded.
    // If a goal is missing, it is removed from the player's completed goals in the database record.
    private void fixGhostGoals() {
        // Create a new ArrayList to store goals that need to be removed, this avoids ConcurrentModificationException
        ArrayList<String> goalsToRemove = new ArrayList<>();

        for (String completedGoal : completedGoals) {
            if (goalsManager.getGoal(completedGoal) == null) {
                goalsToRemove.add(completedGoal);
            }
        }

        for (String goalToRemove : goalsToRemove) {
            unmarkGoalAsCompleted(goalToRemove);
        }
    }

    private void userMapping() {
        boolean uuidExists = db.playerExists(uuid);
        String existingNickname = uuidExists ? db.getNickname(uuid) : null;
        String existingUUID = db.getUUIDFromNickname(nickname);

        if (uuidExists) {
            // Case 1: UUID exists in database
            if (!nickname.equals(existingNickname)) {
                // Same UUID but different nickname - update nickname
                db.updateNickname(uuid, nickname);
            }
        } else if (existingUUID != null) {
            // Case 2: Nickname exists but with different UUID
            db.updateUUID(uuid, nickname);
        } else {
            // Case 3: New user - neither UUID nor nickname exists
            db.addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }
}