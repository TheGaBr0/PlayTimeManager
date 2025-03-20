package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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
    protected int relativeJoinStreak;
    protected int absoluteJoinStreak;
    protected LinkedHashSet<String> receivedRewards = new LinkedHashSet<>();
    protected LinkedHashSet<String> rewardsToBeClaimed = new LinkedHashSet<>();

    // Private constructor
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime,
                   ArrayList<String> completedGoals, LocalDateTime lastSeen, LocalDateTime firstJoin, int relativeJoinStreak,
                   int absoluteJoinStreak, LinkedHashSet<String> receivedRewards, LinkedHashSet<String> rewardsToBeClaimed) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.completedGoals = completedGoals;
        this.lastSeen = lastSeen;
        this.firstJoin = firstJoin;
        this.relativeJoinStreak = relativeJoinStreak;
        this.absoluteJoinStreak = absoluteJoinStreak;
        this.receivedRewards = receivedRewards;
        this.rewardsToBeClaimed = rewardsToBeClaimed;
    }

    public DBUser(Player p) {
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        userMapping();
        loadUserData();

        if(firstJoin == null){
            firstJoin = LocalDateTime.now();
            db.updateFirstJoin(uuid, firstJoin);
            db.incrementJoinStreak(uuid);
        }
    }

    // Factory method to create DBUser by UUID
    protected static DBUser fromUUID(String uuid) {
        if(uuid == null)
            return null;

        String nickname = db.getNickname(uuid);
        long playtime = db.getPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        ArrayList<String> completedGoals = db.getCompletedGoals(uuid);
        LocalDateTime lastSeen = db.getLastSeen(uuid);
        LocalDateTime firstJoin = db.getFirstJoin(uuid);
        int relativeJoinStreak = db.getRelativeJoinStreak(uuid);
        int absoluteJoinStreak = db.getAbsoluteJoinStreak(uuid);
        LinkedHashSet<String> receivedRewards = db.getReceivedRewards(uuid);
        LinkedHashSet<String> rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, completedGoals, lastSeen, firstJoin, relativeJoinStreak,
                absoluteJoinStreak, receivedRewards, rewardsToBeClaimed);
    }

    // New method to load user data from database
    private void loadUserData() {
        this.DBplaytime = db.getPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
        this.completedGoals = db.getCompletedGoals(uuid);
        this.lastSeen = db.getLastSeen(uuid);
        this.firstJoin = db.getFirstJoin(uuid);
        this.relativeJoinStreak = db.getRelativeJoinStreak(uuid);
        this.absoluteJoinStreak = db.getRelativeJoinStreak(uuid);
        this.receivedRewards = db.getReceivedRewards(uuid);
        this.rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);
    }

    public void reset() {
        this.DBplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;
        this.lastSeen = null;
        this.firstJoin = null;
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;

        // Reset completed goals
        this.completedGoals.clear();
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        // Update all values in database - optimize with a single transaction if possible
        db.updatePlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateCompletedGoals(uuid, completedGoals);
        db.updateLastSeen(uuid, null);
        db.updateFirstJoin(uuid, null);
        db.resetJoinStreaks(uuid);
        db.resetJoinStreaks(uuid);
        db.updateReceivedRewards(uuid, receivedRewards);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
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

    public int getAbsoluteJoinStreak(){
        return absoluteJoinStreak;
    }

    public int getRelativeJoinStreak(){
        return relativeJoinStreak;
    }

    public void incrementJoinStreak(){
        this.relativeJoinStreak++;
        this.absoluteJoinStreak++;
        db.incrementJoinStreak(uuid);
    }

    public void resetJoinStreaks(){
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;
        db.resetJoinStreaks(uuid);
    }

    public void resetRelativeJoinStreak(){
        this.relativeJoinStreak = 0;
        db.resetRelativeJoinStreak(uuid);
    }

    public void migrateUnclaimedRewards(){
        LinkedHashSet<String> newRewardsToBeClaimed = new LinkedHashSet<String>();
        plugin.getLogger().info(String.valueOf(rewardsToBeClaimed));
        for(String reward : rewardsToBeClaimed){
            if (reward != null) {
                if(!reward.endsWith("R")){
                    String modifiedReward = reward + ".R";
                    newRewardsToBeClaimed.add(modifiedReward);
                }
            }
        }
        rewardsToBeClaimed = newRewardsToBeClaimed;
        db.updateRewardsToBeClaimed(uuid, newRewardsToBeClaimed);
    }

    public void unclaimReward(String rewardId) {
        rewardsToBeClaimed.remove(rewardId);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    public void removeRewardToBeClaimed(String rewardId) {
        // Remove all rewards where the integer part matches rewardId
        rewardsToBeClaimed.removeIf(reward -> {
            String mainInstance = reward.split("\\.")[0];
            return mainInstance.equals(rewardId);
        });
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    public void removeReceivedReward(String rewardId) {
        // Remove all rewards where the integer part matches rewardId
        receivedRewards.removeIf(reward -> {
            String mainInstance = reward.split("\\.")[0];
            return mainInstance.equals(rewardId);
        });
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    public void addRewardToBeClaimed(String rewardKey) {
        rewardsToBeClaimed.add(rewardKey);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    public void addReceivedReward(String rewardKey) {
        receivedRewards.add(rewardKey);
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    // Getter methods for reward sets
    public Set<String> getReceivedRewards() {
        return new HashSet<>(receivedRewards); // Return a copy to prevent modification
    }

    public Set<String> getRewardsToBeClaimed() {
        return new HashSet<>(rewardsToBeClaimed); // Return a copy to prevent modification
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