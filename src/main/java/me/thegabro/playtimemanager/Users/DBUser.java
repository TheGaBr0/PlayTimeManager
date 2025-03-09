package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
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
    protected int joinStreak;
    protected LinkedHashSet<Float> receivedRewards = new LinkedHashSet<>();
    protected LinkedHashSet<Float> rewardsToBeClaimed = new LinkedHashSet<>();

    // Private constructor
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime,
                   ArrayList<String> completedGoals, LocalDateTime lastSeen, LocalDateTime firstJoin, int joinStreak,
                   LinkedHashSet<Float> receivedRewards, LinkedHashSet<Float> rewardsToBeClaimed) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.completedGoals = completedGoals;
        this.lastSeen = lastSeen;
        this.firstJoin = firstJoin;
        this.joinStreak = joinStreak;
        this.receivedRewards = receivedRewards;
        this.rewardsToBeClaimed = rewardsToBeClaimed;
        fixGhostGoals();
        fixGhostRewards();
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
        int joinStreak = db.getJoinStreak(uuid);
        LinkedHashSet<Float> receivedRewards = db.getReceivedRewards(uuid);
        LinkedHashSet<Float> rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, completedGoals, lastSeen, firstJoin, joinStreak,
                receivedRewards, rewardsToBeClaimed);
    }

    // New method to load user data from database
    private void loadUserData() {
        this.DBplaytime = db.getPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
        this.completedGoals = db.getCompletedGoals(uuid);
        this.lastSeen = db.getLastSeen(uuid);
        this.firstJoin = db.getFirstJoin(uuid);
        this.joinStreak = db.getJoinStreak(uuid);
        this.receivedRewards = db.getReceivedRewards(uuid);
        this.rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);
    }

    public void reset() {
        this.DBplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;
        this.lastSeen = null;
        this.firstJoin = null;
        this.joinStreak = 0;

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
        db.resetJoinStreak(uuid);
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

    public int getJoinStreak(){
        return joinStreak;
    }

    public void resetJoinStreak(){
        this.joinStreak = 0;
        db.resetJoinStreak(uuid);
    }

    public void removeRewardToBeClaimed(float rewardId) {
        // Remove all rewards where the integer part matches rewardId
        rewardsToBeClaimed.removeIf(rewardFloat -> Math.floor(rewardFloat) == rewardId);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    public void removeReceivedReward(float rewardId) {
        // Remove all rewards where the integer part matches rewardId
        receivedRewards.removeIf(rewardFloat -> Math.floor(rewardFloat) == rewardId);
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    public void dataIntegrityCheck(){
        fixGhostGoals();
        fixGhostRewards();
    }

    // Made public so it can be called as needed
    public void fixGhostGoals() {
        // Create a new ArrayList to store goals that need to be removed
        ArrayList<String> goalsToRemove = new ArrayList<>();

        for (String completedGoal : completedGoals) {
            if (goalsManager.getGoal(completedGoal) == null) {
                goalsToRemove.add(completedGoal);
            }
        }

        // Only update the database if we actually have goals to remove
        if (!goalsToRemove.isEmpty()) {
            for (String goalToRemove : goalsToRemove) {
                unmarkGoalAsCompleted(goalToRemove);
            }
        }
    }

    // Made public so it can be called as needed
    public void fixGhostRewards() {
        // Create sets to store rewards that need to be removed
        Set<Float> receivedRewardsToRemove = new HashSet<>();
        Set<Float> rewardsToBeClaimedToRemove = new HashSet<>();
        JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

        // Check received rewards
        for (Float rewardId : receivedRewards) {
            if (!joinStreaksManager.rewardExists(rewardId)) {
                receivedRewardsToRemove.add(rewardId);
            }
        }

        // Check rewards to be claimed
        for (Float rewardId : rewardsToBeClaimed) {
            if (!joinStreaksManager.rewardExists(rewardId)) {
                rewardsToBeClaimedToRemove.add(rewardId);
            }
        }

        // Only update if we have rewards to remove
        if (!receivedRewardsToRemove.isEmpty()) {
            for (Float rewardToRemove : receivedRewardsToRemove) {
                removeReceivedReward(rewardToRemove);
            }
        }

        if (!rewardsToBeClaimedToRemove.isEmpty()) {
            for (Float rewardToRemove : rewardsToBeClaimedToRemove) {
                removeRewardToBeClaimed(rewardToRemove);
            }
        }
    }

    // Getter methods for reward sets
    public Set<Float> getReceivedRewards() {
        return new HashSet<>(receivedRewards); // Return a copy to prevent modification
    }

    public Set<Float> getRewardsToBeClaimed() {
        return new HashSet<>(rewardsToBeClaimed); // Return a copy to prevent modification
    }

    public void addRewardToBeClaimed(float rewardKey) {
        // Use LinkedHashSet to maintain order
        LinkedHashSet<Float> currentRewards = new LinkedHashSet<>(rewardsToBeClaimed);
        currentRewards.add(rewardKey);
        rewardsToBeClaimed = currentRewards;

        // Update in database immediately to maintain order
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    public void addReceivedReward(float rewardKey) {
        // LinkedHashSet to maintain order
        LinkedHashSet<Float> currentRewards = new LinkedHashSet<>(receivedRewards);
        currentRewards.add(rewardKey);
        receivedRewards = currentRewards;

        // Update in database immediately to maintain order
        db.updateReceivedRewards(uuid, receivedRewards);
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