package Users;

import SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DBUsersManager {
    private final PlayTimeDatabase db;
    private final PlayTimeManager plugin;
    private static volatile DBUsersManager instance;
    private final OnlineUsersManager onlineUsersManager;
    private final List<DBUser> topPlayers;
    private final Map<String, DBUser> userCache;

    private static final int TOP_PLAYERS_LIMIT = 100;

    private DBUsersManager() {
        this.plugin = PlayTimeManager.getInstance();
        this.db = plugin.getDatabase();
        this.topPlayers = Collections.synchronizedList(new ArrayList<>());
        this.userCache = new ConcurrentHashMap<>();
        this.onlineUsersManager = plugin.getOnlineUsersManager();
        updateTopPlayersFromDB();
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

    public DBUser getUserFromNickname(String nickname) {
        // Check online users first
        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(nickname);
        if (onlineUser != null) {
            return onlineUser;
        }

        // Check cache
        return userCache.computeIfAbsent(nickname.toLowerCase(), k -> DBUser.fromNickname(nickname));
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

        // Check cache or create new DBUser
        return userCache.computeIfAbsent(uuid, k -> DBUser.fromUUID(uuid));
    }

    public void updateTopPlayersFromDB() {
        Map<String, String> dbTopPlayers = db.getTopPlayersByPlaytime(TOP_PLAYERS_LIMIT);

        synchronized (topPlayers) {
            topPlayers.clear();
            dbTopPlayers.entrySet().stream()
                    .map(entry -> getUserFromUUID(entry.getKey()))
                    .filter(Objects::nonNull)
                    .forEach(topPlayers::add);
        }
    }

    public void updateCachedTopPlayers(OnlineUser onlineUser) {
        synchronized (topPlayers) {
            for (int i = 0; i < topPlayers.size(); i++) {
                if (topPlayers.get(i).getUuid().equals(onlineUser.getUuid())) {
                    topPlayers.set(i, getUserFromUUID(onlineUser.getUuid()));
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
                    .collect(Collectors.toList());
            return sortedPlayers.get(position - 1);
        }
    }

    public void removeGoalFromAllUsers(String goalName) {
        db.getAllNicknames().stream()
                .map(this::getUserFromNickname)
                .filter(user -> user.hasCompletedGoal(goalName))
                .forEach(user -> user.unmarkGoalAsCompleted(goalName));
    }

    public List<DBUser> getAllDBUsers() {
        return db.getAllNicknames().stream()
                .map(this::getUserFromNickname)
                .collect(Collectors.toList());
    }

    public void clearCache() {
        userCache.clear();
    }
}