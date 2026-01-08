package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
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
    protected Instant lastSeen;
    protected Instant firstJoin;
    protected final GoalsManager goalsManager = GoalsManager.getInstance();
    protected int relativeJoinStreak;
    protected int absoluteJoinStreak;
    protected ArrayList<RewardSubInstance> receivedRewards = new ArrayList<>();
    protected ArrayList<RewardSubInstance> rewardsToBeClaimed = new ArrayList<>();
    protected boolean afk;
    protected OfflinePlayer playerInstance;
    protected ArrayList<String> notReceivedGoals;

    public static final DBUser LOADING = new DBUser(); // Special instance for loading state
    public static final DBUser NOT_FOUND = new DBUser(); // Special instance for not found
    /**
     * Private constructor to create a DBUser with all data loaded from database.
     * Used internally by factory methods.
     */
    private DBUser(String uuid, String nickname, long playtime, long artificialPlaytime, long DBAFKplaytime,
                   ArrayList<String> completedGoals, ArrayList<String> notReceivedGoals ,Instant lastSeen, Instant firstJoin, int relativeJoinStreak,
                   int absoluteJoinStreak, ArrayList<RewardSubInstance> receivedRewards, ArrayList<RewardSubInstance> rewardsToBeClaimed){

        this.uuid = uuid;
        this.nickname = nickname;
        this.DBplaytime = playtime;
        this.artificialPlaytime = artificialPlaytime;
        this.DBAFKplaytime = DBAFKplaytime;
        this.notReceivedGoals = notReceivedGoals;
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
     * Protected constructor for subclass use only (OnlineUser).
     * The subclass is responsible for calling loadUserDataAsync and handling initialization.
     */
    protected DBUser() {
        this.completedGoals = new ArrayList<>();
        this.receivedRewards = new ArrayList<>();
        this.rewardsToBeClaimed = new ArrayList<>();
        this.afk = false;
        this.playerInstance = null;
    }

    /**
     * Asynchronously creates a new DBUser for an active player.
     * Handles user mapping and loads existing data from database.
     *
     * @param p the Player object representing the user
     * @param callback Called when user is fully loaded with the DBUser instance
     */
    public static void createDBUserAsync(Player p, Consumer<DBUser> callback) {
        String uuid = p.getUniqueId().toString();
        String nickname = p.getName();
        long fromServerPlayTime = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DBUser user = new DBUser(uuid, nickname, 0, 0, 0,
                    new ArrayList<>(), new ArrayList<>(), null, null, 0, 0,
                    new ArrayList<>(), new ArrayList<>());
            user.fromServerOnJoinPlayTime = fromServerPlayTime;

            user.userMapping();
            user.loadUserDataSync();

            // Handle legacy null first_join values
            if(user.firstJoin == null){
                user.firstJoin = Instant.now();
                DatabaseHandler.getInstance().getPlayerDAO().updateFirstJoin(uuid, user.firstJoin);
            }

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(user));
        });
    }

    /**
     * Factory method to create DBUser asynchronously from UUID.
     * Loads all user data from the database and creates a new instance.
     *
     * @param uuid the player's unique identifier string
     * @param callback Called with the loaded DBUser instance (or null if UUID is null)
     */
    public static void fromUUIDAsync(String uuid, Consumer<DBUser> callback) {
        if(uuid == null) {
            callback.accept(null);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DBUser user = fromUUIDSync(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(user));
        });
    }

    /**
     * Internal synchronous method to load user from UUID.
     * Should only be called from async contexts.
     */
    private static DBUser fromUUIDSync(String uuid) {
        if(uuid == null)
            return null;

        String nickname = DatabaseHandler.getInstance().getPlayerDAO().getNickname(uuid);
        long playtime = DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid);
        long artificialPlaytime = DatabaseHandler.getInstance().getPlayerDAO().getArtificialPlaytime(uuid);
        long afkplaytime = DatabaseHandler.getInstance().getPlayerDAO().getAFKPlaytime(uuid);
        ArrayList<String> completedGoals = DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid);
        ArrayList<String> notReceivedGoals = DatabaseHandler.getInstance().getGoalsDAO().getNotReceivedGoals(uuid);
        Instant lastSeen = DatabaseHandler.getInstance().getPlayerDAO().getLastSeen(uuid);
        Instant firstJoin = DatabaseHandler.getInstance().getPlayerDAO().getFirstJoin(uuid);
        int relativeJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid);
        int absoluteJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getAbsoluteJoinStreak(uuid);
        ArrayList<RewardSubInstance> receivedRewards = DatabaseHandler.getInstance().getStreakDAO().getReceivedRewards(uuid);
        ArrayList<RewardSubInstance> rewardsToBeClaimed = DatabaseHandler.getInstance().getStreakDAO().getRewardsToBeClaimed(uuid);

        return new DBUser(uuid, nickname, playtime, artificialPlaytime, afkplaytime, completedGoals, notReceivedGoals, lastSeen, firstJoin, relativeJoinStreak,
                absoluteJoinStreak, receivedRewards, rewardsToBeClaimed);
    }

    /**
     * Loads user data from the database into instance variables.
     * Called during async construction to populate all user statistics.
     * Must be called from an async context.
     */
    protected void loadUserDataSync() {
        this.DBplaytime = DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid);
        this.DBAFKplaytime = DatabaseHandler.getInstance().getPlayerDAO().getAFKPlaytime(uuid);
        this.artificialPlaytime = DatabaseHandler.getInstance().getPlayerDAO().getArtificialPlaytime(uuid);
        this.completedGoals = DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid);
        this.lastSeen = DatabaseHandler.getInstance().getPlayerDAO().getLastSeen(uuid);
        this.firstJoin = DatabaseHandler.getInstance().getPlayerDAO().getFirstJoin(uuid);
        this.relativeJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid);
        this.absoluteJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid);
        this.receivedRewards = DatabaseHandler.getInstance().getStreakDAO().getReceivedRewards(uuid);
        this.rewardsToBeClaimed = DatabaseHandler.getInstance().getStreakDAO().getRewardsToBeClaimed(uuid);
        this.notReceivedGoals = DatabaseHandler.getInstance().getGoalsDAO().getNotReceivedGoals(uuid);
    }

    /**
     * Asynchronously loads user data from database and calls callback when complete.
     * Used by subclasses that need to load data after construction.
     *
     * @param callback Called on main thread after data is loaded
     */
    protected void loadUserDataAsync(Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            loadUserDataSync();
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public boolean isOnline() {
        return this instanceof OnlineUser;
    }

    public OfflinePlayer getPlayerInstance() {
        if (playerInstance != null) {
            return playerInstance;
        }

        try {
            playerInstance = Bukkit.getOfflinePlayer(uuid);
            return playerInstance;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID format for player: " + uuid);
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Unexpected error loading player " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    // Getters - these are safe as synchronous since they use cached data
    public Instant getFirstJoin(){ return firstJoin; }
    public Instant getLastSeen() { return lastSeen; }
    public String getUuid() { return uuid; }
    public String getNickname() { return nickname; }

    public long getPlaytime() {
        long totalPlaytime = DBplaytime + artificialPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time", false)) {
            totalPlaytime -= DBAFKplaytime;
        }
        return totalPlaytime;
    }

    public long getPlaytimeWithSnapshot(long playtimeSnapshot) {
        return getPlaytime();
    }

    public long getArtificialPlaytime() {
        return artificialPlaytime;
    }

    public void setArtificialPlaytimeAsync(long artificialPlaytime, Runnable callback) {
        this.artificialPlaytime = artificialPlaytime;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateArtificialPlaytime(uuid, artificialPlaytime);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void setArtificialPlaytime(long artificialPlaytime) {
        setArtificialPlaytimeAsync(artificialPlaytime, null);
    }

    public ArrayList<String> getCompletedGoals(){
        return completedGoals;
    }

    public ArrayList<String> getNotReceivedGoals(){
        return notReceivedGoals;
    }

    public boolean hasCompletedGoal(String goalName){
        return completedGoals.contains(goalName);
    }

    public void markGoalAsReceivedAsync(String goalName, Runnable callback){
        notReceivedGoals.remove(goalName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getGoalsDAO().markGoalAsReceived(uuid, goalName);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }


    public void markGoalAsCompletedAsync(String goalName, boolean received, Runnable callback){
        completedGoals.add(goalName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getGoalsDAO().addCompletedGoal(uuid, nickname, goalName, received);

            if(!received)
                notReceivedGoals.add(goalName);

            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void unmarkGoalAsCompletedAsync(String goalName, Runnable callback){
        completedGoals.remove(goalName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getGoalsDAO().removeCompletedGoal(uuid, goalName);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void reloadGoalsSync(){
        this.completedGoals.clear();
        this.completedGoals = DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid);
    }

    public void unmarkGoalAsCompleted(String goalName){
        unmarkGoalAsCompletedAsync(goalName, null);
    }

    public int getAbsoluteJoinStreak(){
        return absoluteJoinStreak;
    }

    public int getRelativeJoinStreak(){
        return relativeJoinStreak;
    }

    public void incrementRelativeJoinStreakAsync(Runnable callback){
        this.relativeJoinStreak++;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().setRelativeJoinStreak(uuid, this.relativeJoinStreak);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void incrementRelativeJoinStreak(){
        incrementRelativeJoinStreakAsync(null);
    }

    public void incrementAbsoluteJoinStreakAsync(Runnable callback){
        this.absoluteJoinStreak++;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().setAbsoluteJoinStreak(uuid, this.absoluteJoinStreak);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void incrementAbsoluteJoinStreak(){
        incrementAbsoluteJoinStreakAsync(null);
    }

    public void setRelativeJoinStreakAsync(int value, Runnable callback){
        this.relativeJoinStreak = value;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().setRelativeJoinStreak(uuid, this.relativeJoinStreak);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void setRelativeJoinStreak(int value){
        setRelativeJoinStreakAsync(value, null);
    }

    public void resetJoinStreaksAsync(Runnable callback){
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().setAbsoluteJoinStreak(uuid, 0);
            DatabaseHandler.getInstance().getStreakDAO().setRelativeJoinStreak(uuid, 0);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetJoinStreaks(){
        resetJoinStreaksAsync(null);
    }

    public void resetRelativeJoinStreakAsync(Runnable callback){
        this.relativeJoinStreak = 0;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().setRelativeJoinStreak(uuid, 0);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetRelativeJoinStreak(){
        resetRelativeJoinStreakAsync(null);
    }

    public void migrateUnclaimedRewardsAsync(Runnable callback){
        if (rewardsToBeClaimed.isEmpty()) {
            if(callback != null) callback.run();
            return;
        }

        List<RewardSubInstance> expiredRewards = new ArrayList<>();
        for(RewardSubInstance subInstance : rewardsToBeClaimed){
            RewardSubInstance expiredInstance = new RewardSubInstance(
                    subInstance.mainInstanceID(),
                    subInstance.requiredJoins(),
                    true
            );
            expiredRewards.add(expiredInstance);
        }

        rewardsToBeClaimed.clear();
        rewardsToBeClaimed.addAll(expiredRewards);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().markRewardsAsExpired(uuid);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void migrateUnclaimedRewards(){
        migrateUnclaimedRewardsAsync(null);
    }

    public void unclaimRewardAsync(RewardSubInstance rewardSubInstance, Runnable callback) {
        rewardsToBeClaimed.removeIf(unclaimedReward -> unclaimedReward.mainInstanceID().equals(rewardSubInstance.mainInstanceID()) &&
                unclaimedReward.requiredJoins().equals(rewardSubInstance.requiredJoins()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().removeRewardToBeClaimed(uuid, rewardSubInstance);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void unclaimReward(RewardSubInstance rewardSubInstance) {
        unclaimRewardAsync(rewardSubInstance, null);
    }

    public void unreceiveRewardAsync(RewardSubInstance rewardSubInstance, Runnable callback) {
        receivedRewards.removeIf(receivedReward -> receivedReward.mainInstanceID().equals(rewardSubInstance.mainInstanceID()) &&
                receivedReward.requiredJoins().equals(rewardSubInstance.requiredJoins()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().removeReceivedReward(uuid, rewardSubInstance);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void unreceiveReward(RewardSubInstance rewardSubInstance) {
        unreceiveRewardAsync(rewardSubInstance, null);
    }

    public void wipeRewardsToBeClaimed(Integer mainInstanceID) {
        rewardsToBeClaimed.removeIf(r -> Objects.equals(r.mainInstanceID(), mainInstanceID));
    }

    public void wipeReceivedRewards(Integer mainInstanceID) {
        receivedRewards.removeIf(r -> Objects.equals(r.mainInstanceID(), mainInstanceID));
    }

    public void addRewardToBeClaimedAsync(RewardSubInstance rewardSubInstance, Runnable callback) {
        rewardsToBeClaimed.add(rewardSubInstance);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().addRewardToBeClaimed(uuid, nickname, rewardSubInstance);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void addRewardToBeClaimed(RewardSubInstance rewardSubInstance) {
        addRewardToBeClaimedAsync(rewardSubInstance, null);
    }

    public void addReceivedRewardAsync(RewardSubInstance rewardSubInstance, Runnable callback) {
        receivedRewards.add(rewardSubInstance);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().addReceivedReward(uuid, nickname, rewardSubInstance);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void addReceivedReward(RewardSubInstance rewardSubInstance){
        addReceivedRewardAsync(rewardSubInstance, null);
    }


    public ArrayList<RewardSubInstance> getReceivedRewards() {
        return new ArrayList<>(receivedRewards);
    }

    public ArrayList<RewardSubInstance> getRewardsToBeClaimed() {
        return new ArrayList<>(rewardsToBeClaimed);
    }

    /**
     * Handles user mapping logic for UUID and nickname consistency.
     * Updates database records when UUID or nickname changes are detected.
     * Also updates caches to maintain consistency.
     * Must be called from an async context.
     */
    private void userMapping() {
        boolean uuidExists = DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid);
        String existingNickname = uuidExists ? DatabaseHandler.getInstance().getPlayerDAO().getNickname(uuid) : null;
        String existingUUID = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);
        DBUsersManager dbUsersManager = DBUsersManager.getInstance();

        if (uuidExists) {
            if (!nickname.equals(existingNickname)) {
                DatabaseHandler.getInstance().getPlayerDAO().updateNickname(uuid, nickname);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    dbUsersManager.updateNicknameInCache(uuid, existingNickname, nickname);
                });
            }
        } else if (existingUUID != null) {
            DatabaseHandler.getInstance().getPlayerDAO().updateUUID(uuid, nickname);

            Bukkit.getScheduler().runTask(plugin, () -> {
                dbUsersManager.updateUUIDInCache(existingUUID, uuid, nickname);
            });
        } else {
            DatabaseHandler.getInstance().getPlayerDAO().addNewPlayer(uuid, nickname, fromServerOnJoinPlayTime);
        }
    }

    public long getAFKPlaytime() {
        return DBAFKplaytime;
    }

    public long getAFKPlaytimeWithSnapshot(long playtimeSnapshot) {
        return getAFKPlaytime();
    }

    public boolean isAFK() {
        return afk;
    }

    public void setAFK(boolean afk) {
        this.afk = afk;
    }

    public void resetAsync(Runnable callback) {
        this.DBplaytime = 0;
        this.DBAFKplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;
        this.lastSeen = null;
        this.firstJoin = null;
        this.relativeJoinStreak = 0;
        this.absoluteJoinStreak = 0;
        this.completedGoals.clear();
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().resetUserInDatabase(uuid);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void reset() {
        resetAsync(null);
    }

    public void resetPlaytimeAsync(Runnable callback) {
        this.DBplaytime = 0;
        this.DBAFKplaytime = 0;
        this.artificialPlaytime = 0;
        this.fromServerOnJoinPlayTime = 0;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updatePlaytime(uuid, 0);
            DatabaseHandler.getInstance().getPlayerDAO().updateArtificialPlaytime(uuid, 0);
            DatabaseHandler.getInstance().getPlayerDAO().updateAFKPlaytime(uuid, 0);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetPlaytime() {
        resetPlaytimeAsync(null);
    }

    public void resetLastSeenAsync(Runnable callback) {
        this.lastSeen = null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateLastSeen(uuid, null);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetLastSeen() {
        resetLastSeenAsync(null);
    }

    public void resetFirstJoinAsync(Runnable callback) {
        this.firstJoin = null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getPlayerDAO().updateFirstJoin(uuid, null);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetFirstJoin() {
        resetFirstJoinAsync(null);
    }

    public void resetJoinStreakRewardsAsync(Runnable callback) {
        this.receivedRewards.clear();
        this.rewardsToBeClaimed.clear();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getStreakDAO().resetAllUserRewards(uuid);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetJoinStreakRewards() {
        resetJoinStreakRewardsAsync(null);
    }

    public void resetGoalsAsync(Runnable callback) {
        this.completedGoals.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler.getInstance().getGoalsDAO().removeAllGoalsFromUser(uuid);
            if(callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public void resetGoals() {
        resetGoalsAsync(null);
    }
}