package me.thegabro.playtimemanager.JoinStreaks;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class JoinStreaksManager {
    private static JoinStreaksManager instance;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final Set<String> joinedDuringCurrentInterval = new HashSet<>();
    private PlayTimeManager plugin;
    private static PlayTimeDatabase db;
    private DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private BukkitTask intervalTask;

    private JoinStreaksManager() {}

    public static synchronized JoinStreaksManager getInstance() {
        if (instance == null) {
            instance = new JoinStreaksManager();
        }
        return instance;
    }

    public void initialize(PlayTimeManager playTimeManager) {
        this.plugin = playTimeManager;
        db = plugin.getDatabase();
        clearRewards();
        loadRewards();
        populateJoinedUsers();
        startIntervalTask();
    }

    private void populateJoinedUsers() {
        long streakIntervalSeconds = plugin.getConfiguration().getStreakInterval();
        Set<String> recentPlayers = db.getPlayersWithinTimeInterval(streakIntervalSeconds);

        joinedDuringCurrentInterval.clear();
        joinedDuringCurrentInterval.addAll(recentPlayers);

        if(plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().info("Populated join streak interval tracking with " +
                joinedDuringCurrentInterval.size() + " players who joined within the configured interval.");
    }

    public void startIntervalTask() {
        // Cancel existing task if it exists
        if (intervalTask != null) {
            intervalTask.cancel();
        }

        long intervalTicks = plugin.getConfiguration().getStreakInterval() * 20;

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Reset streaks for players who didn't join during this interval
                resetMissingPlayerStreaks();

                if(plugin.getConfiguration().getStreakCheckVerbose())
                    plugin.getLogger().info("Resetting join streak interval tracking. Cleared " +
                        joinedDuringCurrentInterval.size() + " tracked players.");

                joinedDuringCurrentInterval.clear();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void resetMissingPlayerStreaks() {
        // Get all players from database who have active streaks
        Set<String> playersWithStreaks = db.getPlayersWithActiveStreaks();


        // Find players with streaks who didn't join during this interval
        playersWithStreaks.removeAll(joinedDuringCurrentInterval);

        //do not reset online users during time interval expiring, instead increment their joinstreak
        for(OnlineUser onlineUser : onlineUsersManager.getOnlineUsersByUUID().values()){
            playersWithStreaks.remove(onlineUser.getUuid());
            onlineUser.incrementJoinStreak();
        }

        // Reset streaks for all missing players
        for (String playerUUID : playersWithStreaks) {
            // Retrieve player data and reset streak
            DBUser user = dbUsersManager.getUserFromUUID(playerUUID);
            if (user != null) {
                user.resetJoinStreak();
            } else {
                // If user data isn't loaded in memory, reset directly in database
                db.resetJoinStreak(playerUUID);
            }
        }

        if(plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().info("Reset join streaks for " + playersWithStreaks.size() +
                " players who missed the current interval");
    }

    public boolean isItAStreak(OnlineUser user) {
        String playerUUID = user.getUuid();
        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();
        long streakIntervalSeconds = plugin.getConfiguration().getStreakInterval();

        if (secondsBetween <= streakIntervalSeconds) {
            // Check if player already joined during this interval
            if (!joinedDuringCurrentInterval.contains(playerUUID)) {
                // First join in this interval, increment streak and add to tracking set
                user.incrementJoinStreak();
                joinedDuringCurrentInterval.add(playerUUID);
                return true;
            } else {
                // Player already joined during this interval, don't increment streak
                return true; // Still considered a streak, just not incrementing counter
            }
        } else {
            // Too much time has passed, reset streak
            user.resetJoinStreak();
            // Add to tracking set to prevent multiple increments if they join again soon
            joinedDuringCurrentInterval.add(playerUUID);
            return false;
        }
    }

    public void addReward(JoinStreakReward reward) {
        rewards.add(reward);
    }

    public void removeReward(JoinStreakReward reward) {
        rewards.remove(reward);
        reward.deleteFile();
    }

    public JoinStreakReward getReward(String name) {
        return rewards.stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public Set<JoinStreakReward> getRewards() {
        return new HashSet<>(rewards);
    }

    public void clearRewards() {
        rewards.clear();
    }

    public void loadRewards() {
        File RewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (RewardsFolder.exists() && RewardsFolder.isDirectory()) {
            File[] RewardFiles = RewardsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (RewardFiles != null) {
                for (File file : RewardFiles) {
                    String RewardName = file.getName().replace(".yml", "");
                    new JoinStreakReward(plugin, RewardName, 0L, false);
                }
            }
        }
    }
}