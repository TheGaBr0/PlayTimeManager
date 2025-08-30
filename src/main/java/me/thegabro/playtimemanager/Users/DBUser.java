package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

public class DBUser {
    protected String uuid;
    protected String nickname;
    protected long DBplaytime;
    protected long artificialPlaytime;
    protected long DBAFKplaytime;
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
                   int absoluteJoinStreak, LinkedHashSet<String> receivedRewards, LinkedHashSet<String> rewardsToBeClaimed){

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
            db.updateFirstJoin(uuid, firstJoin);
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

        String nickname = db.getNickname(uuid);
        long playtime = db.getPlaytime(uuid);
        long artificialPlaytime = db.getArtificialPlaytime(uuid);
        long afkplaytime = db.getAFKPlaytime(uuid);
        ArrayList<String> completedGoals = db.getCompletedGoals(uuid);
        LocalDateTime lastSeen = db.getLastSeen(uuid);
        LocalDateTime firstJoin = db.getFirstJoin(uuid);
        int relativeJoinStreak = db.getRelativeJoinStreak(uuid);
        int absoluteJoinStreak = db.getAbsoluteJoinStreak(uuid);
        LinkedHashSet<String> receivedRewards = db.getReceivedRewards(uuid);
        LinkedHashSet<String> rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, afkplaytime, completedGoals, lastSeen, firstJoin, relativeJoinStreak,
                absoluteJoinStreak, receivedRewards, rewardsToBeClaimed);
    }

    /**
     * Loads user data from the database into instance variables.
     * Called during construction to populate all user statistics and settings.
     */
    private void loadUserData() {
        this.DBplaytime = db.getPlaytime(uuid);
        this.DBAFKplaytime = db.getAFKPlaytime(uuid);
        this.artificialPlaytime = db.getArtificialPlaytime(uuid);
        this.completedGoals = db.getCompletedGoals(uuid);
        this.lastSeen = db.getLastSeen(uuid);
        this.firstJoin = db.getFirstJoin(uuid);
        this.relativeJoinStreak = db.getRelativeJoinStreak(uuid);
        this.absoluteJoinStreak = db.getRelativeJoinStreak(uuid);
        this.receivedRewards = db.getReceivedRewards(uuid);
        this.rewardsToBeClaimed = db.getRewardsToBeClaimed(uuid);
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
        db.updateArtificialPlaytime(uuid, artificialPlaytime);
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
        db.updateCompletedGoals(uuid, completedGoals);
    }

    /**
     * Removes a goal from the player's completed goals list and updates the database.
     *
     * @param goalName the name of the goal to unmark
     */
    public void unmarkGoalAsCompleted(String goalName){
        completedGoals.remove(goalName);
        db.updateCompletedGoals(uuid, completedGoals);
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
        db.setRelativeJoinStreak(uuid, this.relativeJoinStreak);
    }

    /**
     * Increments the player's absolute join streak by 1 and updates the database.
     */
    public void incrementAbsoluteJoinStreak(){
        this.absoluteJoinStreak++;
        db.setAbsoluteJoinStreak(uuid, this.absoluteJoinStreak);
    }

    /**
     * Sets the player's relative join streak to a specific value and updates the database.
     *
     * @param value the new relative join streak value
     */
    public void setRelativeJoinStreak(int value){
        this.relativeJoinStreak = value;
        db.setRelativeJoinStreak(uuid, this.relativeJoinStreak);
    }

    /**
     * Resets both relative and absolute join streaks to 0 and updates the database.
     */
    public void resetJoinStreaks(){
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;
        db.setAbsoluteJoinStreak(uuid, 0);
        db.setRelativeJoinStreak(uuid, 0);
    }

    /**
     * Resets the relative join streak to 0 and updates the database.
     */
    public void resetRelativeJoinStreak(){
        this.relativeJoinStreak = 0;
        db.setRelativeJoinStreak(uuid, 0);
    }

    /**
     * Migrates unclaimed rewards to the new format by adding ".R" suffix if not present.
     * Preserves existing rewards that already have the correct format.
     */
    public void migrateUnclaimedRewards(){
        LinkedHashSet<String> newRewardsToBeClaimed = new LinkedHashSet<String>();
        for(String reward : rewardsToBeClaimed){
            if (reward != null) {
                if(!reward.endsWith("R")){
                    String modifiedReward = reward + ".R";
                    newRewardsToBeClaimed.add(modifiedReward);
                }else{
                    //do not delete already stored unclaimed rewards of previous cycles
                    newRewardsToBeClaimed.add(reward);
                }
            }
        }
        rewardsToBeClaimed = newRewardsToBeClaimed;
        db.updateRewardsToBeClaimed(uuid, newRewardsToBeClaimed);
    }

    /**
     * Removes a specific reward from the player's unclaimed rewards and updates the database.
     *
     * @param rewardId the ID of the reward to unclaim
     */
    public void unclaimReward(String rewardId) {
        rewardsToBeClaimed.remove(rewardId);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    /**
     * Removes a specific reward from the player's received rewards and updates the database.
     *
     * @param rewardId the ID of the reward to unreceive
     */
    public void unreceiveReward(String rewardId) {
        receivedRewards.remove(rewardId);
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    /**
     * Removes all unclaimed rewards that match the specified reward ID (by main instance).
     * Compares the integer part before the dot separator.
     *
     * @param rewardId the main reward ID to wipe from unclaimed rewards
     */
    public void wipeRewardToBeClaimed(String rewardId) {
        // Remove all rewards where the integer part matches rewardId
        rewardsToBeClaimed.removeIf(reward -> {
            String mainInstance = reward.split("\\.")[0];
            return mainInstance.equals(rewardId);
        });
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    /**
     * Removes all received rewards that match the specified reward ID (by main instance).
     * Compares the integer part before the dot separator.
     *
     * @param rewardId the main reward ID to wipe from received rewards
     */
    public void wipeReceivedReward(String rewardId) {
        // Remove all rewards where the integer part matches rewardId
        receivedRewards.removeIf(reward -> {
            String mainInstance = reward.split("\\.")[0];
            return mainInstance.equals(rewardId);
        });
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    /**
     * Adds a reward to the player's unclaimed rewards list and updates the database.
     *
     * @param rewardKey the reward key to add to unclaimed rewards
     */
    public void addRewardToBeClaimed(String rewardKey) {
        rewardsToBeClaimed.add(rewardKey);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    /**
     * Adds a reward to the player's received rewards list and updates the database.
     *
     * @param rewardKey the reward key to add to received rewards
     */
    public void addReceivedReward(String rewardKey) {
        receivedRewards.add(rewardKey);
        db.updateReceivedRewards(uuid, receivedRewards);
    }

    /**
     * Returns a copy of the player's received rewards set.
     *
     * @return a new HashSet containing all received reward IDs
     */
    public Set<String> getReceivedRewards() {
        return new HashSet<>(receivedRewards); // Return a copy to prevent modification
    }

    /**
     * Returns a copy of the player's unclaimed rewards set.
     *
     * @return a new HashSet containing all unclaimed reward IDs
     */
    public Set<String> getRewardsToBeClaimed() {
        return new HashSet<>(rewardsToBeClaimed); // Return a copy to prevent modification
    }

    /**
     * Handles user mapping logic for UUID and nickname consistency.
     * Updates database records when UUID or nickname changes are detected.
     * Creates new player record if neither UUID nor nickname exists.
     */
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

        // Update all values in database
        // TODO: optimize with a single transaction
        db.updatePlaytime(uuid, 0);
        db.updateAFKPlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateCompletedGoals(uuid, completedGoals);
        db.updateLastSeen(uuid, null);
        db.updateFirstJoin(uuid, null);
        db.setRelativeJoinStreak(uuid, 0);
        db.setAbsoluteJoinStreak(uuid, 0);
        db.updateReceivedRewards(uuid, receivedRewards);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
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

        db.updatePlaytime(uuid, 0);
        db.updateArtificialPlaytime(uuid, 0);
        db.updateAFKPlaytime(uuid, 0);
    }

    /**
     * Resets the player's last seen timestamp to null and updates the database.
     */
    public void resetLastSeen() {
        this.lastSeen = null;
        db.updateLastSeen(uuid, null);
    }

    /**
     * Resets the player's first join timestamp to null and updates the database.
     */
    public void resetFirstJoin() {
        this.firstJoin = null;
        db.updateFirstJoin(uuid, null);
    }

    /**
     * Resets all join streak rewards (both received and unclaimed) and updates the database.
     */
    public void resetJoinStreakRewards() {
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        db.updateReceivedRewards(uuid, receivedRewards);
        db.updateRewardsToBeClaimed(uuid, rewardsToBeClaimed);
    }

    /**
     * Resets the player's completed goals list and updates the database.
     * Clears all goal completion progress.
     */
    public void resetGoals() {
        this.completedGoals.clear();
        db.updateCompletedGoals(uuid, completedGoals);
    }
}