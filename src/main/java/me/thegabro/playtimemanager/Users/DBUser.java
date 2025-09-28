package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;

public class DBUser {
    protected String uuid;
    protected String nickname;
    protected long DBplaytime;
    protected long artificialPlaytime;
    protected long DBAFKplaytime;
    protected static final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected long fromServerOnJoinPlayTime;
    protected ArrayList<String> completedGoals;
    protected static DatabaseHandler db = DatabaseHandler.getInstance();
    protected LocalDateTime lastSeen;
    protected LocalDateTime firstJoin;
    protected final GoalsManager goalsManager = GoalsManager.getInstance();
    protected int relativeJoinStreak;
    protected int absoluteJoinStreak;
    protected ArrayList<RewardSubInstance> receivedRewards = new ArrayList<>();
    protected ArrayList<RewardSubInstance> rewardsToBeClaimed = new ArrayList<>();
    protected boolean afk;
    protected OfflinePlayer playerInstance;

    /**
     * Private constructor to create a DBUser with all data loaded from database.
     * Used internally by the factory method fromUUID.
     *
     * @param uuid the player's unique identifier
     * @param nickname the player's display name
     * @param playtime the player's total playtime in ticks
     * @param artificialPlaytime additional playtime added artificially
     * @param DBAFKplaytime the player's AFK time in ticks
     * @param completedGoals list of goals the player has completed
     * @param lastSeen timestamp of when the player was last seen
     * @param firstJoin timestamp of when the player first joined
     * @param relativeJoinStreak current relative join streak
     * @param absoluteJoinStreak total absolute join streak
     * @param receivedRewards set of rewards the player has received
     * @param rewardsToBeClaimed set of rewards waiting to be claimed
     */
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime, long DBAFKplaytime,
                   ArrayList<String> completedGoals, LocalDateTime lastSeen, LocalDateTime firstJoin, int relativeJoinStreak,
                   int absoluteJoinStreak, ArrayList<RewardSubInstance> receivedRewards, ArrayList<RewardSubInstance> rewardsToBeClaimed){

        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.DBAFKplaytime = DBAFKplaytime;
        this.completedGoals = completedGoals;
        this.lastSeen = lastSeen;
        this.firstJoin = firstJoin;
        this.relativeJoinStreak = relativeJoinStreak;
        this.absoluteJoinStreak = absoluteJoinStreak;
        this.receivedRewards = receivedRewards;
        this.rewardsToBeClaimed = rewardsToBeClaimed;
        afk = false;
        playerInstance = null;
    }

    /**
     * Constructs a new DBUser for an active player.
     * Handles user mapping and loads existing data from database.
     * Initializes first join timestamp if null for legacy compatibility.
     *
     * @param p the Player object representing the user
     */
    public DBUser(Player p) {
        this.uuid = p.getUniqueId().toString();
        this.nickname = p.getName();
        this.fromServerOnJoinPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        userMapping();
        loadUserData();

        //LEAVE THIS HERE: since first_join field was added some releases later...some servers may have null
        // first_join values for older players into their db. If such players join and this check isn't here: KABOOM.
        if(firstJoin == null){
            firstJoin = LocalDateTime.now();
            db.getPlayerDAO().updateFirstJoin(uuid, firstJoin);
        }
    }

    /**
     * Factory method to create a DBUser instance by UUID.
     * Loads all user data from the database and creates a new instance.
     *
     * @param uuid the player's unique identifier string
     * @return a new DBUser instance with data loaded from database, or null if UUID is null
     */
    protected static DBUser fromUUID(String uuid) {
        if(uuid == null)
            return null;

        String nickname = db.getPlayerDAO().getNickname(uuid);
        long playtime = db.getPlayerDAO().getPlaytime(uuid);
        long artificialPlaytime = db.getPlayerDAO().getArtificialPlaytime(uuid);
        long afkplaytime = db.getPlayerDAO().getAFKPlaytime(uuid);
        ArrayList<String> completedGoals = db.getGoalsDAO().getCompletedGoals(uuid);
        LocalDateTime lastSeen = db.getPlayerDAO().getLastSeen(uuid);
        LocalDateTime firstJoin = db.getPlayerDAO().getFirstJoin(uuid);
        int relativeJoinStreak = db.getStreakDAO().getRelativeJoinStreak(uuid);
        int absoluteJoinStreak = db.getStreakDAO().getAbsoluteJoinStreak(uuid);
        ArrayList<RewardSubInstance> receivedRewards = db.getStreakDAO().getReceivedRewards(uuid);
        ArrayList<RewardSubInstance> rewardsToBeClaimed = db.getStreakDAO().getRewardsToBeClaimed(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, afkplaytime, completedGoals, lastSeen, firstJoin, relativeJoinStreak,
                absoluteJoinStreak, receivedRewards, rewardsToBeClaimed);
    }

    /**
     * Loads user data from the database into instance variables.
     * Called during construction to populate all user statistics and settings.
     */
    private void loadUserData() {
        this.DBplaytime = db.getPlayerDAO().getPlaytime(uuid);
        this.DBAFKplaytime = db.getPlayerDAO().getAFKPlaytime(uuid);
        this.artificialPlaytime = db.getPlayerDAO().getArtificialPlaytime(uuid);
        this.completedGoals = db.getGoalsDAO().getCompletedGoals(uuid);
        this.lastSeen = db.getPlayerDAO().getLastSeen(uuid);
        this.firstJoin = db.getPlayerDAO().getFirstJoin(uuid);
        this.relativeJoinStreak = db.getStreakDAO().getRelativeJoinStreak(uuid);
        this.absoluteJoinStreak = db.getStreakDAO().getRelativeJoinStreak(uuid);
        this.receivedRewards = db.getStreakDAO().getReceivedRewards(uuid);
        this.rewardsToBeClaimed = db.getStreakDAO().getRewardsToBeClaimed(uuid);
        afk = false;
    }

    /**
     * Checks if this user is currently online.
     *
     * @return true if the user is an instance of OnlineUser, false otherwise
     */
    public boolean isOnline() {
        return this instanceof OnlineUser;
    }

    /**
     * Gets the OfflinePlayer instance asynchronously to avoid blocking the main thread.
     * Uses cached instance if available, otherwise fetches from Bukkit asynchronously.
     *
     * @param callback Consumer function to handle the OfflinePlayer result
     */
    public void getPlayerInstance(Consumer<OfflinePlayer> callback) {
        if (playerInstance != null) {
            callback.accept(playerInstance);
            return;
        }

        // Run asynchronously to prevent main thread blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID parsedUuid = UUID.fromString(uuid);
                OfflinePlayer player = Bukkit.getOfflinePlayer(parsedUuid);

                // Update on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    playerInstance = player;
                    callback.accept(player);
                });
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID format for player: " + uuid);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            }
        });
    }

    /**
     * Returns the timestamp when the player first joined the server.
     *
     * @return the first join LocalDateTime
     */
    public LocalDateTime getFirstJoin(){ return firstJoin; }

    /**
     * Returns the timestamp when the player was last seen on the server.
     *
     * @return the last seen LocalDateTime
     */
    public LocalDateTime getLastSeen() { return lastSeen; }

    /**
     * Returns the player's unique identifier.
     *
     * @return the UUID string
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Returns the player's display name/nickname.
     *
     * @return the player's nickname
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Calculates and returns the player's total playtime.
     * Combines database playtime with artificial playtime, optionally excluding AFK time.
     *
     * @return the total playtime in ticks
     */
    public long getPlaytime() {
        long totalPlaytime = DBplaytime + artificialPlaytime;

        if (plugin.getConfiguration().getBoolean("ignore-afk-time")) {
            totalPlaytime -= DBAFKplaytime;
        }

        return totalPlaytime;
    }

    /**
     * Gets playtime using a snapshot value - base implementation for offline users
     * For offline users, snapshot is ignored and regular getPlaytime() is used
     *
     * @param playtimeSnapshot Ignored for offline users
     * @return Total playtime in ticks
     */
    public long getPlaytimeWithSnapshot(long playtimeSnapshot) {
        // For offline users, just return regular playtime calculation
        return getPlaytime();
    }


    /**
     * Returns the player's artificial playtime (manually added time).
     *
     * @return the artificial playtime in ticks
     */
    public long getArtificialPlaytime() {
        return artificialPlaytime;
    }

    /**
     * Sets the player's artificial playtime and updates the database.
     *
     * @param artificialPlaytime the new artificial playtime value in ticks
     */
    public void setArtificialPlaytime(long artificialPlaytime) {
        this.artificialPlaytime = artificialPlaytime;
        db.getPlayerDAO().updateArtificialPlaytime(uuid, artificialPlaytime);
    }

    /**
     * Returns a list of goals the player has completed.
     *
     * @return ArrayList of completed goal names
     */
    public ArrayList<String> getCompletedGoals(){
        return completedGoals;
    }

    /**
     * Checks if the player has completed a specific goal.
     *
     * @param goalName the name of the goal to check
     * @return true if the goal has been completed, false otherwise
     */
    public boolean hasCompletedGoal(String goalName){
        return completedGoals.contains(goalName);
    }

    /**
     * Marks a goal as completed for the player and updates the database.
     *
     * @param goalName the name of the goal to mark as completed
     */
    public void markGoalAsCompleted(String goalName){
        completedGoals.add(goalName);
        db.getGoalsDAO().addCompletedGoal(uuid, nickname, goalName);
    }

    /**
     * Removes a goal from the player's completed goals list and updates the database.
     *
     * @param goalName the name of the goal to unmark
     */
    public void unmarkGoalAsCompleted(String goalName){
        completedGoals.remove(goalName);
        db.getGoalsDAO().removeCompletedGoal(uuid, goalName);
    }

    /**
     * Returns the player's absolute join streak count.
     *
     * @return the absolute join streak value
     */
    public int getAbsoluteJoinStreak(){
        return absoluteJoinStreak;
    }

    /**
     * Returns the player's relative join streak count.
     *
     * @return the relative join streak value
     */
    public int getRelativeJoinStreak(){
        return relativeJoinStreak;
    }

    /**
     * Increments the player's relative join streak by 1 and updates the database.
     */
    public void incrementRelativeJoinStreak(){
        this.relativeJoinStreak++;
        db.getStreakDAO().setRelativeJoinStreak(uuid, this.relativeJoinStreak);
    }

    /**
     * Increments the player's absolute join streak by 1 and updates the database.
     */
    public void incrementAbsoluteJoinStreak(){
        this.absoluteJoinStreak++;
        db.getStreakDAO().setAbsoluteJoinStreak(uuid, this.absoluteJoinStreak);
    }

    /**
     * Sets the player's relative join streak to a specific value and updates the database.
     *
     * @param value the new relative join streak value
     */
    public void setRelativeJoinStreak(int value){
        this.relativeJoinStreak = value;
        db.getStreakDAO().setRelativeJoinStreak(uuid, this.relativeJoinStreak);
    }

    /**
     * Resets both relative and absolute join streaks to 0 and updates the database.
     */
    public void resetJoinStreaks(){
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;
        db.getStreakDAO().setAbsoluteJoinStreak(uuid, 0);
        db.getStreakDAO().setRelativeJoinStreak(uuid, 0);
    }

    /**
     * Resets the relative join streak to 0 and updates the database.
     */
    public void resetRelativeJoinStreak(){
        this.relativeJoinStreak = 0;
        db.getStreakDAO().setRelativeJoinStreak(uuid, 0);
    }

    /**
     * Marks all currently unclaimed rewards as expired
     * Called when a reward cycle ends to handle rewards not claimed within the time limit.
     */
    public void migrateUnclaimedRewards(){
        if (rewardsToBeClaimed.isEmpty()) {
            return;
        }

        // Create new expired instances and replace the old ones
        List<RewardSubInstance> expiredRewards = new ArrayList<>();
        for(RewardSubInstance subInstance : rewardsToBeClaimed){
            // Create a new record with expired = true
            RewardSubInstance expiredInstance = new RewardSubInstance(
                    subInstance.mainInstanceID(),
                    subInstance.requiredJoins(),
                    true
            );
            expiredRewards.add(expiredInstance);
        }

        // Replace the old list with expired instances
        rewardsToBeClaimed.clear();
        rewardsToBeClaimed.addAll(expiredRewards);

        // Mark all current unclaimed rewards as expired in the database
        db.getStreakDAO().markRewardsAsExpired(uuid);
    }

    /**
     * Removes a specific reward from the player's unclaimed rewards.
     *
     * @param rewardSubInstance the reward to remove from unclaimed rewards
     */
    public void unclaimReward(RewardSubInstance rewardSubInstance) {

        rewardsToBeClaimed.removeIf(unclaimedReward -> unclaimedReward.mainInstanceID().equals(rewardSubInstance.mainInstanceID()) &&
                unclaimedReward.requiredJoins().equals(rewardSubInstance.requiredJoins()));

        db.getStreakDAO().removeRewardToBeClaimed(uuid, rewardSubInstance);
    }

    /**
     * Removes a specific reward from the player's received rewards.
     *
     * @param rewardSubInstance the reward to remove from received rewards
     */
    public void unreceiveReward(RewardSubInstance rewardSubInstance) {

        receivedRewards.removeIf(receivedReward -> receivedReward.mainInstanceID().equals(rewardSubInstance.mainInstanceID()) &&
                receivedReward.requiredJoins().equals(rewardSubInstance.requiredJoins()));

        db.getStreakDAO().removeReceivedReward(uuid,  rewardSubInstance);
    }

    /**
     * Removes all unclaimed rewards that share the same main instance ID.
     *
     * @param mainInstanceID the reward containing the main instance ID to match against
     */
    public void wipeRewardsToBeClaimed(Integer mainInstanceID) {
        rewardsToBeClaimed.removeIf(r -> Objects.equals(r.mainInstanceID(), mainInstanceID));
    }

    /**
     * Removes all received rewards that share the same main instance ID.
     *
     * @param mainInstanceID the reward containing the main instance ID to match against
     */
    public void wipeReceivedRewards(Integer mainInstanceID) {
        receivedRewards.removeIf(r -> Objects.equals(r.mainInstanceID(), mainInstanceID));
    }

    /**
     * Adds a reward to the player's unclaimed rewards list.
     *
     * @param rewardSubInstance the reward to add to unclaimed rewards
     */
    public void addRewardToBeClaimed(RewardSubInstance rewardSubInstance) {
        rewardsToBeClaimed.add(rewardSubInstance);
        db.getStreakDAO().addRewardToBeClaimed(uuid,  nickname, rewardSubInstance);
    }

    /**
     * Adds a reward to the player's received rewards list.
     *
     * @param rewardSubInstance the reward to add to received rewards
     */
    public void addReceivedReward(RewardSubInstance rewardSubInstance) {
        receivedRewards.add(rewardSubInstance);
        db.getStreakDAO().addReceivedReward(uuid, nickname, rewardSubInstance);
    }

    /**
     * Returns a copy of the player's received rewards.
     *
     * @return a new list containing all received rewards
     */
    public ArrayList<RewardSubInstance> getReceivedRewards() {
        return new ArrayList<>(receivedRewards); // Return a copy to prevent modification
    }

    /**
     * Returns a copy of the player's unclaimed rewards.
     *
     * @return a new list containing all unclaimed rewards
     */
    public ArrayList<RewardSubInstance> getRewardsToBeClaimed() {
        return new ArrayList<>(rewardsToBeClaimed); // Return a copy to prevent modification
    }

    /**
     * Handles user mapping logic for UUID and nickname consistency.
     * Updates database records when UUID or nickname changes are detected.
     * Creates new player record if neither UUID nor nickname exists.
     */
    private void userMapping() {
        boolean uuidExists = db.getPlayerDAO().playerExists(uuid);
        String existingNickname = uuidExists ? db.getPlayerDAO().getNickname(uuid) : null;
        String existingUUID = db.getPlayerDAO().getUUIDFromNickname(nickname);

        if (uuidExists) {
            // Case 1: UUID exists in database
            if (!nickname.equals(existingNickname)) {
                // Same UUID but different nickname - update nickname
                db.getPlayerDAO().updateNickname(uuid, nickname);
            }
        } else if (existingUUID != null) {
            // Case 2: Nickname exists but with different UUID
            db.getPlayerDAO().updateUUID(uuid, nickname);
        } else {
            // Case 3: New user - neither UUID nor nickname exists
            db.getPlayerDAO().addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }

    /**
     * Returns the player's total AFK playtime from the database.
     *
     * @return the AFK playtime in ticks
     */
    public long getAFKPlaytime() {
        return DBAFKplaytime;
    }

    /**
     * Gets AFK playtime using a snapshot value - base implementation for offline users
     * For offline users, snapshot is ignored and regular getAFKPlaytime() is used
     *
     * @param playtimeSnapshot Ignored for offline users
     * @return Total AFK playtime in ticks
     */
    public long getAFKPlaytimeWithSnapshot(long playtimeSnapshot) {
        // For offline users, just return regular AFK playtime
        return getAFKPlaytime();
    }

    /**
     * Returns the player's current AFK status.
     *
     * @return true if player is currently AFK, false otherwise
     */
    public boolean isAFK() {
        return afk;
    }

    /**
     * Sets the player's AFK status.
     *
     * @param afk true to mark player as AFK, false otherwise
     */
    public void setAFK(boolean afk) {
        this.afk = afk;
    }

    /**
     * Completely resets all player data to default values.
     * Clears playtime, goals, rewards, streaks, and timestamps.
     * Updates all values in the database (TODO: optimize with single transaction).
     */
    public void reset() {
        this.DBplaytime = 0;
        this.DBAFKplaytime = 0;
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

        db.getPlayerDAO().resetUserInDatabase(uuid);
    }

    /**
     * Resets all playtime-related data to 0.
     * Includes regular playtime, AFK time, and artificial playtime.
     */
    public void resetPlaytime() {
        this.DBplaytime = 0;
        this.DBAFKplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;

        db.getPlayerDAO().updatePlaytime(uuid, 0);
        db.getPlayerDAO().updateArtificialPlaytime(uuid, 0);
        db.getPlayerDAO().updateAFKPlaytime(uuid, 0);
    }

    /**
     * Resets the player's last seen timestamp to null and updates the database.
     */
    public void resetLastSeen() {
        this.lastSeen = null;
        db.getPlayerDAO().updateLastSeen(uuid, null);
    }

    /**
     * Resets the player's first join timestamp to null and updates the database.
     */
    public void resetFirstJoin() {
        this.firstJoin = null;
        db.getPlayerDAO().updateFirstJoin(uuid, null);
    }

    /**
     * Resets all join streak rewards (both received and unclaimed) and updates the database.
     */
    public void resetJoinStreakRewards() {
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        db.getStreakDAO().resetAllUserRewards(uuid);
    }

    /**
     * Resets the player's completed goals list and updates the database.
     * Clears all goal completion progress.
     */
    public void resetGoals() {
        this.completedGoals.clear();
        db.getGoalsDAO().removeAllGoalsFromUser(uuid);
    }
}