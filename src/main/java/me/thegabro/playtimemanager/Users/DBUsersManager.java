package me.thegabro.playtimemanager.Users;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DBUsersManager {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static volatile DBUsersManager instance;
    private final List<DBUser> topPlayers;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    private final Configuration configuration = Configuration.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private static final int TOP_PLAYERS_LIMIT = 100;

    private final BiMap<String, String> nicknameUuidCache;
    private final Map<String, DBUser> userCache;
    private final Set<String> nonExistentUsers = ConcurrentHashMap.newKeySet();


    private List<String> playersHiddenFromLeaderBoard;
    private DBUsersManager() {
        this.topPlayers = Collections.synchronizedList(new ArrayList<>());
        this.userCache = new ConcurrentHashMap<>();
        this.nicknameUuidCache = Maps.synchronizedBiMap(HashBiMap.create());
        this.playersHiddenFromLeaderBoard = new ArrayList<>();

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
            updateTopPlayersFromDB();// DB work stays async

            Bukkit.getScheduler().runTask(plugin, this::clearCaches);
        }, clearInterval, clearInterval);
    }

    public void getUserFromNicknameAsync(String nickname, Consumer<DBUser> callback) {

        if(nonExistentUsers.contains(nickname.toLowerCase())){
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        }else{
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Get UUID from DB (runs in async thread)
                String uuid = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);

                if (uuid == null) {
                    // Player not found
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                    return;
                }

                // Once we have the UUID, load the user (async again)
                getUserFromUUIDAsync(uuid, callback);
            });
        }
    }

    public void getUserFromNicknameAsyncWithContext(String nickname, String context, Consumer<DBUser> callback) {

        if(nonExistentUsers.contains(nickname.toLowerCase())) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        }else{
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Get UUID from DB
                String uuid = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);

                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                    return;
                }

                // Load user from UUID with context
                getUserFromUUIDAsyncWithContext(uuid, context, callback);
            });
        }
    }

    public DBUser getUserFromUUIDSync(String uuid) {
        CompletableFuture<DBUser> future = new CompletableFuture<>();

        getUserFromUUIDAsync(uuid, future::complete);

        try {
            return future.get(); // Blocks until the async call completes
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void getUserFromUUIDAsync(String uuid, Consumer<DBUser> callback) {
        // Fast path: check if player is online
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(onlineUser));
            return;
        }

        // Fast path: check cache (on main thread before going async)
        DBUser cached = userCache.get(uuid);
        if (cached != null) {
            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Cache hit for player: " + cached.getNickname() + " from context: unknown");
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
            return;
        }

        if (plugin.CACHE_DEBUG) {
            plugin.getLogger().info("Cache miss for UUID: " + uuid + " from context: unknown");
        }

        // Slow path: database query
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Loading player from DB: " + uuid + " from context: unknown");
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                // Update cache on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (user != null) {
                        userCache.put(uuid, user);
                        nicknameUuidCache.put(user.getNickname().toLowerCase(), uuid);

                        if (plugin.CACHE_DEBUG) {
                            plugin.getLogger().info("Cached player: " + user.getNickname());
                        }
                    }
                    callback.accept(user);
                });
            });
        });
    }

    public void getUserFromUUIDAsyncWithContext(String uuid, String context, Consumer<DBUser> callback) {
        // Fast path: check if player is online
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(onlineUser));
            return;
        }

        // Fast path: check cache (on main thread before going async)
        DBUser cached = userCache.get(uuid);
        if (cached != null) {
            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Cache hit for player: " + cached.getNickname() + " from context: " + context);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
            return;
        }

        if (plugin.CACHE_DEBUG) {
            plugin.getLogger().info("Cache miss for UUID: " + uuid + " from context: " + context);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Loading player from DB: " + uuid + " from context: " + context);
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                // Update cache on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (user != null) {
                        userCache.put(uuid, user);
                        nicknameUuidCache.put(user.getNickname().toLowerCase(), uuid);

                        if (plugin.CACHE_DEBUG) {
                            plugin.getLogger().info("Cached player: " + user.getNickname());
                        }
                    }
                    callback.accept(user);
                });
            });
        });
    }

    /**
     * Synchronously retrieves a user from cache only (no DB lookup)
     * First checks online users, then uses nickname→UUID index for O(1) cache lookup
     * Returns null if not found in cache
     *
     * @param nickname The player's nickname
     * @return The cached DBUser or null if not in cache
     */
    public DBUser getUserFromCacheSync(String nickname) {

        String lowerNickname = nickname.toLowerCase();
        if (nonExistentUsers.contains(lowerNickname)) {
            return null;
        }

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(nickname);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Use nickname index for O(1) lookup
        String uuid = nicknameUuidCache.get(nickname.toLowerCase());
        if (uuid == null) {
            return null;
        }

        return userCache.get(uuid);
    }

    public boolean isKnownNonExistent(String nickname) {
        return nonExistentUsers.contains(nickname.toLowerCase());
    }

    public void markAsNonExistent(String nickname) {
        nonExistentUsers.add(nickname.toLowerCase());
    }

    public void markAsExistent(String nickname) {
        nonExistentUsers.remove(nickname.toLowerCase());
    }

    public void updateTopPlayersFromDB() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Wait for online users' playtime update to finish
                onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                playersHiddenFromLeaderBoard = plugin.getConfiguration().getStringList("placeholders.playtime-leaderboard-blacklist", new ArrayList<>());

                // Fetch more players from DB to account for blacklisted ones
                Map<String, String> dbTopPlayers = DatabaseHandler.getInstance().getStatisticsDAO()
                        .getTopPlayersByPlaytime(TOP_PLAYERS_LIMIT + playersHiddenFromLeaderBoard.size());

                // Load all DBUsers asynchronously
                List<CompletableFuture<DBUser>> futures = dbTopPlayers.keySet().stream()
                        .map(uuid -> {
                            CompletableFuture<DBUser> future = new CompletableFuture<>();
                            getUserFromUUIDAsyncWithContext(uuid, "leaderboard-update", future::complete);
                            return future;
                        })
                        .toList();

                // Wait for all async loads to complete
                CompletableFuture
                        .allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRunAsync(() -> {
                            // Collect and filter loaded users
                            List<DBUser> validTopPlayers = futures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .filter(user -> !playersHiddenFromLeaderBoard.contains(user.getNickname()))
                                    .limit(TOP_PLAYERS_LIMIT)
                                    .toList();

                            // Update leaderboard safely on the main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                synchronized (topPlayers) {
                                    topPlayers.clear();
                                    topPlayers.addAll(validTopPlayers);
                                }
                            });
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
        // Skip hidden players
        if (playersHiddenFromLeaderBoard.contains(onlineUser.getNickname()))
            return;

        String uuid = onlineUser.getUuid();

        // Check if player already exists in the leaderboard
        boolean alreadyPresent;
        synchronized (topPlayers) {
            alreadyPresent = topPlayers.stream().anyMatch(player -> player.getUuid().equals(uuid));
        }

        // Load player data asynchronously (non-blocking)
        getUserFromUUIDAsyncWithContext(uuid, "cached leaderboard update", user -> {
            if (user == null)
                return; // Not found in DB, skip

            Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (topPlayers) {
                    // Re-check if player exists (atomic check-and-act)
                    boolean exists = topPlayers.stream().anyMatch(p -> p.getUuid().equals(uuid));

                    if (!exists && topPlayers.size() < TOP_PLAYERS_LIMIT) {
                        topPlayers.add(user);
                    } else if (exists) {
                        // Update existing entry
                        for (int i = 0; i < topPlayers.size(); i++) {
                            if (topPlayers.get(i).getUuid().equals(uuid)) {
                                topPlayers.set(i, user);
                                break;
                            }
                        }
                    }
                }
            });
        });
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
        DatabaseHandler.getInstance().getGoalsDAO().removeGoalFromAllUsers(goalName);
    }

    public void removeRewardFromAllUsers(Integer mainInstanceID){
        for(OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()){
            user.wipeReceivedRewards(mainInstanceID);
            user.wipeRewardsToBeClaimed(mainInstanceID);
        }
        DatabaseHandler.getInstance().getStreakDAO().removeRewardFromAllUsers(mainInstanceID);
    }

    /**
     * Updates the nickname in cache when a player changes their name.
     * Must be called from the main thread.
     *
     * @param uuid The player's UUID
     * @param oldNickname The old nickname to remove from index
     * @param newNickname The new nickname to add to index
     */
    public void updateNicknameInCache(String uuid, String oldNickname, String newNickname) {
        // Remove old nickname mapping (this also removes the reverse uuid → oldNickname)
        if (oldNickname != null) {
            nicknameUuidCache.remove(oldNickname.toLowerCase());
        }

        // Add new nickname mapping
        DBUser cachedUser = userCache.get(uuid);
        if (cachedUser != null) {
            nicknameUuidCache.put(newNickname.toLowerCase(), uuid);
        }

        if (plugin.CACHE_DEBUG) {
            plugin.getLogger().info("Updated nickname in cache: " + oldNickname + " -> " + newNickname + " for UUID: " + uuid);
        }
    }

    /**
     * Updates the UUID in cache when a player's UUID changes (rare case).
     * Must be called from the main thread.
     *
     * @param oldUUID The old UUID to remove
     * @param newUUID The new UUID to add
     * @param nickname The player's nickname
     */
    public void updateUUIDInCache(String oldUUID, String newUUID, String nickname) {
        // Move user in cache
        DBUser user = userCache.remove(oldUUID);
        if (user != null) {
            userCache.put(newUUID, user);
        }

        // Remove old UUID mapping using inverse (removes both directions)
        nicknameUuidCache.inverse().remove(oldUUID);

        // Add new mapping
        nicknameUuidCache.put(nickname.toLowerCase(), newUUID);

        if (plugin.CACHE_DEBUG) {
            plugin.getLogger().info("Updated UUID in cache: " + oldUUID + " -> " + newUUID + " for nickname: " + nickname);
        }
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
        DBUser user = userCache.remove(uuid);
        if (user != null) {
            nicknameUuidCache.remove(user.getNickname().toLowerCase());
        }
    }

    public void clearCaches() {
        userCache.clear();
        nicknameUuidCache.clear(); // Add this
        commandsConfiguration.clearCache();
        configuration.clearCache();
        guIsConfiguration.clearCache();
    }
}