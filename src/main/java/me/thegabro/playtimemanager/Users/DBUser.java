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

/**
 * Represents a player known to the database, whether they are currently online or not.
 *
 * All fields are populated asynchronously from the database. Mutating methods follow the
 * pattern of updating the in-memory state immediately, then persisting to the database on
 * a background thread. Callbacks run back on the main thread when provided.
 *
 * {@link OnlineUser} extends this class to add live, session-aware data.
 */
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

    // The last-seen value from the previous session, captured on join before it is overwritten.
    // Used by CycleScheduler to determine streak eligibility.
    protected Instant previousSessionLastSeen;

    /** Sentinel value returned while a user is still being loaded from the database. */
    public static final DBUser LOADING = new DBUser();
    /** Sentinel value returned when a lookup finds no matching player. */
    public static final DBUser NOT_FOUND = new DBUser();

    /**
     * Full constructor used by factory methods once all data has been read from the database.
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
     * Minimal constructor for subclass use (OnlineUser) and sentinel instances.
     * The caller is responsible for populating fields via {@link #loadUserDataAsync}.
     */
    protected DBUser() {
        this.completedGoals = new ArrayList<>();
        this.receivedRewards = new ArrayList<>();
        this.rewardsToBeClaimed = new ArrayList<>();
        this.afk = false;
        this.playerInstance = null;
    }

    /**
     * Asynchronously creates a DBUser for a player who just joined the server.
     * Runs user mapping (UUID/nickname consistency checks) and loads all data from the database.
     * Fires {@code callback} on the main thread once the user is ready.
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

            // Handle legacy records where first_join was never set
            if(user.firstJoin == null){
                user.firstJoin = Instant.now();
                DatabaseHandler.getInstance().getPlayerDAO().updateFirstJoin(uuid, user.firstJoin);
            }

            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(user));
        });
    }

    /**
     * Loads a DBUser by UUID asynchronously, reading all fields from the database.
     * Calls {@code callback} with null if the UUID is null.
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
     * Synchronous internal helper for loading a user by UUID.
     * Must only be called from an async context to avoid blocking the main thread.
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
     * Populates all instance fields from the database.
     * Must be called from an async context.
     */
    protected void loadUserDataSync() {
        this.DBplaytime = DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid);
        this.DBAFKplaytime = DatabaseHandler.getInstance().getPlayerDAO().getAFKPlaytime(uuid);
        this.artificialPlaytime = DatabaseHandler.getInstance().getPlayerDAO().getArtificialPlaytime(uuid);
        this.completedGoals = DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid);
        this.previousSessionLastSeen = DatabaseHandler.getInstance().getPlayerDAO().getLastSeen(uuid);
        this.lastSeen = this.previousSessionLastSeen;
        this.firstJoin = DatabaseHandler.getInstance().getPlayerDAO().getFirstJoin(uuid);
        this.relativeJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid);
        this.absoluteJoinStreak = DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid);
        this.receivedRewards = DatabaseHandler.getInstance().getStreakDAO().getReceivedRewards(uuid);
        this.rewardsToBeClaimed = DatabaseHandler.getInstance().getStreakDAO().getRewardsToBeClaimed(uuid);
        this.notReceivedGoals = DatabaseHandler.getInstance().getGoalsDAO().getNotReceivedGoals(uuid);
    }

    /**
     * Kicks off an async database load and fires {@code callback} on the main thread when done.
     * Used by subclasses that need to load data after construction.
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

    // --- Getters (safe to call synchronously; all values are cached in memory) ---

    public Instant getFirstJoin(){ return firstJoin; }
    public Instant getLastSeen() { return lastSeen; }
    public String getUuid() { return uuid; }
    public String getNickname() { return nickname; }

    /**
     * Returns the effective playtime, optionally subtracting AFK time
     * based on the {@code ignore-afk-time} config setting.
     */
    public long getPlaytime() {
        long totalPlaytime = DBplaytime + artificialPlaytime;
        if (plugin.getConfiguration().getBoolean("ignore-afk-time", false)) {
            totalPlaytime -= DBAFKplaytime;
        }
        return totalPlaytime;
    }

    /** Overridden by OnlineUser to include the current unsaved session. Base implementation ignores the snapshot. */
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

    /**
     * Marks all pending (unclaimed) rewards as expired.
     * Used when a cycle ends and the player hasn't claimed their rewards yet,
     * so they can still be claimed once in the next cycle but not re-earned freely.
     */
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

    /** Removes all in-memory reward tracking for the given main instance, without touching the database. */
    public void wipeRewardsToBeClaimed(Integer mainInstanceID) {
        rewardsToBeClaimed.removeIf(r -> Objects.equals(r.mainInstanceID(), mainInstanceID));
    }

    /** Removes all in-memory received-reward records for the given main instance, without touching the database. */
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

    /** Returns a snapshot of received rewards to avoid exposing the internal list. */
    public ArrayList<RewardSubInstance> getReceivedRewards() {
        return new ArrayList<>(receivedRewards);
    }

    /** Returns a snapshot of rewards pending claim to avoid exposing the internal list. */
    public ArrayList<RewardSubInstance> getRewardsToBeClaimed() {
        return new ArrayList<>(rewardsToBeClaimed);
    }

    /**
     * Ensures UUID and nickname records are consistent when a player joins.
     * Handles three cases: nickname change (same UUID), UUID change (same nickname, e.g. cracked→premium),
     * and brand-new player. Also updates the in-memory caches on the main thread.
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

    /** Overridden by OnlineUser to include AFK time from the current session. */
    public long getAFKPlaytimeWithSnapshot(long playtimeSnapshot) {
        return getAFKPlaytime();
    }

    public boolean isAFK() {
        return afk;
    }

    public void setAFK(boolean afk) {
        this.afk = afk;
    }

    /** Wipes all stored data for this user both in memory and in the database. */
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