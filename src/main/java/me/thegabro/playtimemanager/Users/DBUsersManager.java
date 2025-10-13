package me.thegabro.playtimemanager.Users;

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
    private final DatabaseHandler db = DatabaseHandler.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private static volatile DBUsersManager instance;
    private final List<DBUser> topPlayers;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    private final Configuration configuration = Configuration.getInstance();
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    private static final int TOP_PLAYERS_LIMIT = 100;

    private final Map<String, String> nicknameToUuidCache;
    private final Map<String, DBUser> userCache;


    private List<String> playersHiddenFromLeaderBoard;
    private DBUsersManager() {
        this.topPlayers = Collections.synchronizedList(new ArrayList<>());
        this.userCache = new ConcurrentHashMap<>();
        this.nicknameToUuidCache = new ConcurrentHashMap<>();

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

    public void getUserFromNicknameAsync(String nickname, Consumer<DBUser> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get UUID from DB (runs in async thread)
            String uuid = db.getPlayerDAO().getUUIDFromNickname(nickname);

            if (uuid == null) {
                // Player not found
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            // Once we have the UUID, load the user (async again)
            getUserFromUUIDAsync(uuid, callback);
        });
    }

    public void getUserFromNicknameAsyncWithContext(String nickname, String context, Consumer<DBUser> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get UUID from DB
            String uuid = db.getPlayerDAO().getUUIDFromNickname(nickname);

            if (uuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            // Load user from UUID with context
            getUserFromUUIDAsyncWithContext(uuid, context, callback);
        });
    }

    public void getUserFromUUIDAsync(String uuid, Consumer<DBUser> callback) {
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            callback.accept(onlineUser);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!db.getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Looking for player: " + db.getPlayerDAO().getNickname(uuid) + " from context: unknown");
                plugin.getLogger().info("Presence in cache: " + userCache.containsKey(uuid));
            }

            DBUser cached = userCache.get(uuid);
            if (cached != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
                return;
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                if (user != null) {
                    userCache.put(uuid, user);
                    nicknameToUuidCache.put(user.getNickname().toLowerCase(), uuid); // Add index
                }
                callback.accept(user);
            });
        });
    }

    public void getUserFromUUIDAsyncWithContext(String uuid, String context, Consumer<DBUser> callback) {
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            callback.accept(onlineUser);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!db.getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (plugin.CACHE_DEBUG) {
                plugin.getLogger().info("Looking for player: " + db.getPlayerDAO().getNickname(uuid) + " from context: " + context);
                plugin.getLogger().info("Presence in cache: " + userCache.containsKey(uuid));
            }

            DBUser cached = userCache.get(uuid);
            if (cached != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
                return;
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                if (user != null) {
                    userCache.put(uuid, user);
                    nicknameToUuidCache.put(user.getNickname().toLowerCase(), uuid); // Add index
                }
                callback.accept(user);
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
        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(nickname);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Use nickname index for O(1) lookup
        String uuid = nicknameToUuidCache.get(nickname.toLowerCase());
        if (uuid == null) {
            return null;
        }

        return userCache.get(uuid);
    }

    public void updateTopPlayersFromDB() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Wait for online users' playtime update to finish
                onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                playersHiddenFromLeaderBoard = plugin.getConfiguration().getStringList("placeholders.playtime-leaderboard-blacklist");

                // Fetch more players from DB to account for blacklisted ones
                Map<String, String> dbTopPlayers = db.getStatisticsDAO()
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
                    if (!alreadyPresent) {
                        // Add if leaderboard not full and not already in list
                        if (topPlayers.size() < TOP_PLAYERS_LIMIT) {
                            topPlayers.add(user);
                        }
                    } else {
                        // Update existing entry with fresh data
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
        db.getGoalsDAO().removeGoalFromAllUsers(goalName);
    }

    public void removeRewardFromAllUsers(Integer mainInstanceID){
        for(OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()){
            user.wipeReceivedRewards(mainInstanceID);
            user.wipeRewardsToBeClaimed(mainInstanceID);
        }
        db.getStreakDAO().removeRewardFromAllUsers(mainInstanceID);
    }

    public void getAllDBUsersAsync(Consumer<List<DBUser>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Fetch all nicknames (DB call)
            List<String> nicknames = db.getPlayerDAO().getAllNicknames();

            // Convert each nickname to a DBUser asynchronously
            List<CompletableFuture<DBUser>> futures = nicknames.stream()
                    .map(nickname -> {
                        CompletableFuture<DBUser> future = new CompletableFuture<>();
                        getUserFromNicknameAsyncWithContext(nickname, "getAllDBUsers", future::complete);
                        return future;
                    })
                    .toList();

            // Combine all futures
            CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRunAsync(() -> {
                        // Collect results
                        List<DBUser> users = futures.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        // Return the final list safely on the main thread
                        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(users));
                    });
        });
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
            nicknameToUuidCache.remove(user.getNickname().toLowerCase());
        }
    }

    public void clearCaches() {
        userCache.clear();
        nicknameToUuidCache.clear(); // Add this
        commandsConfiguration.clearCache();
        configuration.clearCache();
        guIsConfiguration.clearCache();
    }
}