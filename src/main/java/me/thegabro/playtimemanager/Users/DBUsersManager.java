package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DBUsersManager {
    private final PlayTimeDatabase db;
    private final PlayTimeManager plugin;
    private static volatile DBUsersManager instance;
    private final List<DBUser> topPlayers;
    private final Map<String, DBUser> userCache;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    private final Configuration configuration = Configuration.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private static final int TOP_PLAYERS_LIMIT = 100;
    private List<String> playersHiddenFromLeaderBoard;
    private DBUsersManager() {
        this.plugin = PlayTimeManager.getInstance();
        this.db = plugin.getDatabase();
        this.topPlayers = Collections.synchronizedList(new ArrayList<>());
        this.userCache = new ConcurrentHashMap<>();

        startCacheMaintenanceTask();
    }

    public static DBUsersManager getInstance() {
        if (instance == null) {
            synchronized (DBUsersManager.class) {
                if (instance == null) {
                    instance = new DBUsersManager();
                }
            }
        }
        return instance;
    }

    private void startCacheMaintenanceTask() {
        long clearInterval = 6 * 60 * 60 * 20; //Clear every 6 hours (in ticks)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            clearCaches();
            updateTopPlayersFromDB();
        }, clearInterval, clearInterval);
    }

    public DBUser getUserFromNickname(String nickname) {
        // First, try to get UUID from database
        String uuid = db.getUUIDFromNickname(nickname);

        // If UUID exists, use it to retrieve or create the DBUser
        if (uuid != null) {
            return getUserFromUUID(uuid);
        }

        return null;
    }

    public DBUser getUserFromNicknameWithContext(String nickname, String context) {
        // First, try to get UUID from database
        String uuid = db.getUUIDFromNickname(nickname);

        // If UUID exists, use it to retrieve or create the DBUser
        if (uuid != null) {
            return getUserFromUUIDWithContext(uuid, context);
        }

        return null;
    }

    public DBUser getUserFromUUID(String uuid) {
        // Check online users first
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Check if player exists in database
        if (!db.playerExists(uuid)) {
            return null;
        }

        if(plugin.CACHE_DEBUG) {
            plugin.getLogger().info("Looking for player: " + db.getNickname(uuid) + " from context: unknown");
            plugin.getLogger().info("Presence in cache:" + String.valueOf(userCache.containsKey(uuid)));
        }

        // Check cache or create new DBUser
        return userCache.computeIfAbsent(uuid, k -> DBUser.fromUUID(uuid));
    }


    public DBUser getUserFromUUIDWithContext(String uuid, String context) {
        // Check online users first
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Check if player exists in database
        if (!db.playerExists(uuid)) {
            return null;
        }

        if(plugin.CACHE_DEBUG){
            plugin.getLogger().info("Looking for player: "+db.getNickname(uuid)+" from context: "+context);
            plugin.getLogger().info("Presence in cache:"+ String.valueOf(userCache.containsKey(uuid)));
        }

        // Check cache or create new DBUser
        return userCache.computeIfAbsent(uuid, k -> DBUser.fromUUID(uuid));
    }



    public void updateTopPlayersFromDB() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                playersHiddenFromLeaderBoard = plugin.getConfiguration().getStringList("placeholders.playtime-leaderboard-blacklist");

                // Fetch more players from DB to account for those that will be filtered out
                Map<String, String> dbTopPlayers = db.getTopPlayersByPlaytime(TOP_PLAYERS_LIMIT + playersHiddenFromLeaderBoard.size());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<DBUser> validTopPlayers = dbTopPlayers.keySet().stream()
                            .map(this::getUserFromUUID)
                            .filter(Objects::nonNull)
                            .filter(user -> !playersHiddenFromLeaderBoard.contains(user.getNickname()))
                            .limit(TOP_PLAYERS_LIMIT)
                            .toList();

                    synchronized (topPlayers) {
                        topPlayers.clear();
                        topPlayers.addAll(validTopPlayers);
                    }
                });


            } catch (InterruptedException | ExecutionException e) {
                plugin.getLogger().severe("Error updating top players: " + e.getMessage());
            }
        });
    }

    /**
     * Updates the cached top players list when a player joins the server.
     * This method maintains a synchronized cache of top players for performance optimization.
     *
     * @param onlineUser The online user who just joined the server
     */
    public void updateCachedTopPlayers(OnlineUser onlineUser) {

        // Skip hidden players!!
        if(playersHiddenFromLeaderBoard.contains(onlineUser.getNickname()))
            return;

        // Synchronize access to prevent concurrent modification of the topPlayers list
        synchronized (topPlayers) {
            // If the leaderboard isn't full and the player isn't already in the list, add them
            if (topPlayers.size() < TOP_PLAYERS_LIMIT &&
                    topPlayers.stream().noneMatch(player -> player.getUuid().equals(onlineUser.getUuid()))) {
                // Add the player to the cached top players list using fresh database data
                topPlayers.add(getUserFromUUIDWithContext(onlineUser.getUuid(), "Add player to not full cached leaderboard"));
            }

            // Update existing player data in the cache if they're already present
            for (int i = 0; i < topPlayers.size(); i++) {
                if (topPlayers.get(i).getUuid().equals(onlineUser.getUuid())) {
                    // Replace the cached entry with fresh data from database
                    // This ensures playtime and other stats are up-to-date
                    topPlayers.set(i, getUserFromUUIDWithContext(onlineUser.getUuid(), "replace player data in cached leaderboard"));
                    break;
                }
            }
        }
    }

    public DBUser getTopPlayerAtPosition(int position) {
        if (position < 1 || position > topPlayers.size()) {
            return null;
        }

        synchronized (topPlayers) {
            List<DBUser> sortedPlayers = topPlayers.stream()
                    .sorted(Comparator.comparing(DBUser::getPlaytime).reversed())
                    .toList();
            return sortedPlayers.get(position - 1);
        }
    }

    public List<DBUser> getTopPlayers(){
        synchronized (topPlayers) {
            return topPlayers.stream()
                    .sorted(Comparator.comparing(DBUser::getPlaytime).reversed())
                    .collect(Collectors.toList());
        }
    }

    public void removeGoalFromAllUsers(String goalName) {
        for(OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()){
            user.unmarkGoalAsCompleted(goalName);
        }
        db.removeGoalFromAllUsers(goalName);
    }

    public void removeRewardFromAllUsers(String rewardID){
        for(OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()){
            user.wipeReceivedReward(rewardID);
            user.wipeRewardToBeClaimed(rewardID);
        }
        db.removeRewardFromAllUsers(rewardID);
    }

    public List<DBUser> getAllDBUsers() {
        return db.getAllNicknames().stream()
                .map(this::getUserFromNickname)
                .collect(Collectors.toList());
    }

    public List<String> getPlayersHiddenFromLeaderBoard(){
        return new ArrayList<>(playersHiddenFromLeaderBoard);
    }

    public void hidePlayerFromLeaderBoard(String nickname){
        playersHiddenFromLeaderBoard.add(nickname);
        plugin.getConfiguration().set("placeholders.playtime-leaderboard-blacklist", playersHiddenFromLeaderBoard);
    }

    public void unhidePlayerFromLeaderBoard(String nickname){
        playersHiddenFromLeaderBoard.remove(nickname);
        plugin.getConfiguration().set("placeholders.playtime-leaderboard-blacklist", playersHiddenFromLeaderBoard);
    }

    public void removeUserFromCache(String uuid) {
        userCache.remove(uuid);
    }

    public void clearCaches() {
        userCache.clear();
        commandsConfiguration.clearCache();
        configuration.clearCache();
        guIsConfiguration.clearCache();
    }
}