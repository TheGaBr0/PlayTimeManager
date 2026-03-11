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
    private final boolean cacheDebugEnabled;

    private List<String> playersHiddenFromLeaderBoard;

    private DBUsersManager() {
        this.topPlayers = Collections.synchronizedList(new ArrayList<>());
        this.userCache = new ConcurrentHashMap<>();
        this.nicknameUuidCache = Maps.synchronizedBiMap(HashBiMap.create());
        this.playersHiddenFromLeaderBoard = new ArrayList<>();
        this.cacheDebugEnabled = plugin.isDebugCacheEnabled();
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

    /**
     * Normalizes a nickname for use as a cache/lookup key.
     * On online (premium) servers, Mojang guarantees nickname uniqueness is
     * case-insensitive, so we normalize to lowercase for consistent lookups.
     * On offline servers, casing may distinguish different players, so we
     * preserve the original casing.
     *
     * @param nickname The raw nickname input
     * @return The normalized nickname key
     */
    private String normalizeNickname(String nickname) {
        return plugin.getServer().getOnlineMode() ? nickname.toLowerCase() : nickname;
    }

    private void startCacheMaintenanceTask() {
        long clearInterval = 6 * 60 * 60 * 20; // Clear every 6 hours (in ticks)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateTopPlayersFromDB(); // DB work stays async

            Bukkit.getScheduler().runTask(plugin, this::clearCaches);
        }, clearInterval, clearInterval);
    }

    public void getUserFromNicknameAsync(String nickname, Consumer<DBUser> callback) {
        String normalizedNickname = normalizeNickname(nickname);

        if (nonExistentUsers.contains(normalizedNickname)) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String uuid = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);

                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                    return;
                }

                getUserFromUUIDAsync(uuid, callback);
            });
        }
    }

    public void getUserFromNicknameAsyncWithContext(String nickname, String context, Consumer<DBUser> callback) {
        String normalizedNickname = normalizeNickname(nickname);

        if (nonExistentUsers.contains(normalizedNickname)) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String uuid = DatabaseHandler.getInstance().getPlayerDAO().getUUIDFromNickname(nickname);

                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                    return;
                }

                getUserFromUUIDAsyncWithContext(uuid, context, callback);
            });
        }
    }

    public DBUser getUserFromUUIDSync(String uuid) {
        CompletableFuture<DBUser> future = new CompletableFuture<>();

        getUserFromUUIDAsync(uuid, future::complete);

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void getUserFromUUIDAsync(String uuid, Consumer<DBUser> callback) {
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(onlineUser));
            return;
        }

        DBUser cached = userCache.get(uuid);
        if (cached != null) {
            if (cacheDebugEnabled) {
                plugin.getLogger().info("Cache hit for player: " + cached.getNickname() + " from context: unknown");
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
            return;
        }

        if (cacheDebugEnabled) {
            plugin.getLogger().info("Cache miss for UUID: " + uuid + " from context: unknown");
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (cacheDebugEnabled) {
                plugin.getLogger().info("Loading player from DB: " + uuid + " from context: unknown");
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (user != null) {
                        userCache.put(uuid, user);
                        nicknameUuidCache.put(normalizeNickname(user.getNickname()), uuid);

                        if (cacheDebugEnabled) {
                            plugin.getLogger().info("Cached player: " + user.getNickname());
                        }
                    }
                    callback.accept(user);
                });
            });
        });
    }

    public void getUserFromUUIDAsyncWithContext(String uuid, String context, Consumer<DBUser> callback) {
        OnlineUser onlineUser = onlineUsersManager.getOnlineUserByUUID(uuid);
        if (onlineUser != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(onlineUser));
            return;
        }

        DBUser cached = userCache.get(uuid);
        if (cached != null) {
            if (cacheDebugEnabled) {
                plugin.getLogger().info("Cache hit for player: " + cached.getNickname() + " from context: " + context);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached));
            return;
        }

        if (cacheDebugEnabled) {
            plugin.getLogger().info("Cache miss for UUID: " + uuid + " from context: " + context);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!DatabaseHandler.getInstance().getPlayerDAO().playerExists(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                return;
            }

            if (cacheDebugEnabled) {
                plugin.getLogger().info("Loading player from DB: " + uuid + " from context: " + context);
            }

            DBUser.fromUUIDAsync(uuid, user -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (user != null) {
                        userCache.put(uuid, user);
                        nicknameUuidCache.put(normalizeNickname(user.getNickname()), uuid);

                        if (cacheDebugEnabled) {
                            plugin.getLogger().info("Cached player: " + user.getNickname());
                        }
                    }
                    callback.accept(user);
                });
            });
        });
    }

    /**
     * Synchronously retrieves a user from cache only (no DB lookup).
     * First checks online users, then uses nickname→UUID index for O(1) cache lookup.
     * Returns null if not found in cache.
     *
     * @param nickname The player's nickname
     * @return The cached DBUser or null if not in cache
     */
    public DBUser getUserFromCacheSync(String nickname) {
        String normalizedNickname = normalizeNickname(nickname);

        if (nonExistentUsers.contains(normalizedNickname)) {
            return null;
        }

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(nickname);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Use normalized key — consistent with how entries are stored
        String uuid = nicknameUuidCache.get(normalizedNickname);
        if (uuid == null) {
            return null;
        }

        return userCache.get(uuid);
    }

    public boolean isKnownNonExistent(String nickname) {
        return nonExistentUsers.contains(normalizeNickname(nickname));
    }

    public void markAsNonExistent(String nickname) {
        nonExistentUsers.add(normalizeNickname(nickname));
    }

    public void markAsExistent(String nickname) {
        nonExistentUsers.remove(normalizeNickname(nickname));
    }

    public void updateTopPlayersFromDB() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                onlineUsersManager.updateAllOnlineUsersPlaytimeAsync().get();

                playersHiddenFromLeaderBoard = plugin.getConfiguration().getStringList("placeholders.playtime-leaderboard-blacklist", new ArrayList<>());

                Map<String, String> dbTopPlayers = DatabaseHandler.getInstance().getStatisticsDAO()
                        .getTopPlayersByPlaytime(TOP_PLAYERS_LIMIT + playersHiddenFromLeaderBoard.size());

                List<CompletableFuture<DBUser>> futures = dbTopPlayers.keySet().stream()
                        .map(uuid -> {
                            CompletableFuture<DBUser> future = new CompletableFuture<>();
                            getUserFromUUIDAsyncWithContext(uuid, "leaderboard-update", future::complete);
                            return future;
                        })
                        .toList();

                CompletableFuture
                        .allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRunAsync(() -> {
                            List<DBUser> validTopPlayers = futures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .filter(user -> !playersHiddenFromLeaderBoard.contains(user.getNickname()))
                                    .limit(TOP_PLAYERS_LIMIT)
                                    .toList();

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
     *
     * @param onlineUser The online user who just joined the server
     */
    public void updateCachedTopPlayers(OnlineUser onlineUser) {
        if (playersHiddenFromLeaderBoard.contains(onlineUser.getNickname()))
            return;

        String uuid = onlineUser.getUuid();

        getUserFromUUIDAsyncWithContext(uuid, "cached leaderboard update", user -> {
            if (user == null)
                return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (topPlayers) {
                    int index = -1;
                    for (int i = 0; i < topPlayers.size(); i++) {
                        if (topPlayers.get(i).getUuid().equals(uuid)) {
                            index = i;
                            break;
                        }
                    }

                    if (index >= 0) {
                        topPlayers.set(index, user);
                    } else {
                        topPlayers.add(user);
                    }

                    topPlayers.sort(Comparator.comparing(DBUser::getPlaytime).reversed());

                    if (topPlayers.size() > TOP_PLAYERS_LIMIT) {
                        topPlayers.remove(topPlayers.size() - 1);
                    }
                }
            });
        });
    }

    public DBUser getTopPlayerAtPosition(int position) {
        synchronized (topPlayers) {
            if (position < 1 || position > topPlayers.size()) {
                return null;
            }
            return topPlayers.get(position - 1);
        }
    }

    public List<DBUser> getTopPlayers() {
        synchronized (topPlayers) {
            return List.copyOf(topPlayers);
        }
    }

    public void removeGoalFromAllUsers(String goalName) {
        for (OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()) {
            user.unmarkGoalAsCompleted(goalName);
        }
        DatabaseHandler.getInstance().getGoalsDAO().removeGoalFromAllUsers(goalName);
    }

    public void removeRewardFromAllUsers(Integer mainInstanceID) {
        for (OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()) {
            user.wipeReceivedRewards(mainInstanceID);
            user.wipeRewardsToBeClaimed(mainInstanceID);
        }
        DatabaseHandler.getInstance().getStreakDAO().removeRewardFromAllUsers(mainInstanceID);
    }

    /**
     * Updates the nickname in cache when a player changes their name.
     * Must be called from the main thread.
     *
     * @param uuid        The player's UUID
     * @param oldNickname The old nickname to remove from index
     * @param newNickname The new nickname to add to index
     */
    public void updateNicknameInCache(String uuid, String oldNickname, String newNickname) {
        if (oldNickname != null) {
            nicknameUuidCache.remove(normalizeNickname(oldNickname));
        }

        DBUser cachedUser = userCache.get(uuid);
        if (cachedUser != null) {
            nicknameUuidCache.put(normalizeNickname(newNickname), uuid);
        }

        if (cacheDebugEnabled) {
            plugin.getLogger().info("Updated nickname in cache: " + oldNickname + " -> " + newNickname + " for UUID: " + uuid);
        }
    }

    /**
     * Updates the UUID in cache when a player's UUID changes (rare case).
     * Must be called from the main thread.
     *
     * @param oldUUID  The old UUID to remove
     * @param newUUID  The new UUID to add
     * @param nickname The player's nickname
     */
    public void updateUUIDInCache(String oldUUID, String newUUID, String nickname) {
        DBUser user = userCache.remove(oldUUID);
        if (user != null) {
            userCache.put(newUUID, user);
        }

        nicknameUuidCache.inverse().remove(oldUUID);
        nicknameUuidCache.put(normalizeNickname(nickname), newUUID);

        if (cacheDebugEnabled) {
            plugin.getLogger().info("Updated UUID in cache: " + oldUUID + " -> " + newUUID + " for nickname: " + nickname);
        }
    }

    public List<String> getPlayersHiddenFromLeaderBoard() {
        return new ArrayList<>(playersHiddenFromLeaderBoard);
    }

    public void hidePlayerFromLeaderBoard(String nickname) {
        playersHiddenFromLeaderBoard.add(nickname);
        plugin.getConfiguration().set("placeholders.playtime-leaderboard-blacklist", playersHiddenFromLeaderBoard);
    }

    public void unhidePlayerFromLeaderBoard(String nickname) {
        playersHiddenFromLeaderBoard.remove(nickname);
        plugin.getConfiguration().set("placeholders.playtime-leaderboard-blacklist", playersHiddenFromLeaderBoard);
    }

    public void removeUserFromCache(String uuid) {
        DBUser user = userCache.remove(uuid);
        if (user != null) {
            nicknameUuidCache.remove(normalizeNickname(user.getNickname()));
        }
    }

    public void clearCaches() {
        userCache.clear();
        nicknameUuidCache.clear();
        commandsConfiguration.clearCache();
        configuration.clearCache();
        guIsConfiguration.clearCache();
    }
}