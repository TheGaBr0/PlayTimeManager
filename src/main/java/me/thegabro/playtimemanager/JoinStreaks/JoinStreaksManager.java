package me.thegabro.playtimemanager.JoinStreaks;

import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class JoinStreaksManager {
    private static JoinStreaksManager instance;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final Map<Integer, LinkedHashSet<Float>> joinRewardsMap = new HashMap<>();
    private final Set<String> joinedDuringCurrentInterval = new HashSet<>();
    private PlayTimeManager plugin;
    private static PlayTimeDatabase db;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
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

    // Rest of existing code...

    private void populateJoinedUsers() {
        long streakIntervalSeconds = plugin.getConfiguration().getStreakInterval();
        Set<String> recentPlayers = db.getPlayersWithinTimeInterval(streakIntervalSeconds);

        joinedDuringCurrentInterval.clear();
        joinedDuringCurrentInterval.addAll(recentPlayers);

        if(plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().info("Populated join streak interval tracking with " +
                    joinedDuringCurrentInterval.size() + " players who joined within the configured interval.");
    }

    public int getNextRewardId() {
        // Find the maximum ID from the rewards set, or 0 if the set is empty
        return rewards.stream()
                .mapToInt(JoinStreakReward::getId)  // Extract the IDs from rewards (as int)
                .max()                              // Find the maximum ID
                .orElse(0) + 1;                     // Default to 0 if empty, then add 1 for the next ID
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

        if(plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().info(String.format("Join streak check schedule started, refresh rate is %ss", plugin.getConfiguration().getStreakInterval()));

        // Get all players from database who have active streaks
        Set<String> playersWithStreaks = db.getPlayersWithActiveStreaks();


        // Find players with streaks who didn't join during this interval
        playersWithStreaks.removeAll(joinedDuringCurrentInterval);

        //do not reset online users during time interval expiring, instead increment their joinstreak
        for(OnlineUser onlineUser : onlineUsersManager.getOnlineUsersByUUID().values()){
            playersWithStreaks.remove(onlineUser.getUuid());
            onlineUser.incrementJoinStreak();
            checkRewardsForUser(onlineUser, onlineUser.getPlayer());
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

    public void isItAStreak(OnlineUser user, Player player) {
        String playerUUID = user.getUuid();
        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();
        long streakIntervalSeconds = plugin.getConfiguration().getStreakInterval();

        if (secondsBetween <= streakIntervalSeconds) {
            // Check if player already joined during this interval
            if (!joinedDuringCurrentInterval.contains(playerUUID)) {
                // First join in this interval, increment streak and add to tracking set
                user.incrementJoinStreak();
                joinedDuringCurrentInterval.add(playerUUID);
                checkRewardsForUser(user, player);
            }
        } else {
            // Too much time has passed, reset streak
            user.resetJoinStreak();
            // Add to tracking set to prevent multiple increments if they join again soon
            joinedDuringCurrentInterval.add(playerUUID);
        }
    }

    public boolean rewardExists(float rewardFloat) {
        // Extract the integer part of the float (the reward ID)
        int rewardId = (int) Math.floor(rewardFloat);

        // Check if any reward in our set has this ID
        for (JoinStreakReward reward : rewards) {
            if (reward.getId() == rewardId) {
                return true;
            }
        }

        return false;
    }

    public void addReward(JoinStreakReward reward) {
        // Add to our rewards set
        rewards.add(reward);

        updateJoinRewardsMap(reward);

    }
    public LinkedHashSet<Float> getRewardIdsForJoinCount(int joinCount, OnlineUser onlineUser) {
        LinkedHashSet<Float> rewardIds = new LinkedHashSet<>();

        for (Map.Entry<Integer, LinkedHashSet<Float>> entry : joinRewardsMap.entrySet()) {
            int id = entry.getKey();
            JoinStreakReward reward = getReward(id);

            // Get min and max join count for this reward
            int minJoins = reward.getMinRequiredJoins();
            int maxJoins = reward.getMaxRequiredJoins(); // Assuming this method exists or could be added

            if (reward.isSingleJoinReward()) {
                // For single join rewards, check if join count meets the exact requirement
                if (joinCount >= minJoins && minJoins != -1) {
                    rewardIds.add(entry.getValue().iterator().next());
                }
            } else{
                // For interval rewards (e.g., 17-21), add rewards based on position in interval
                if (joinCount >= maxJoins) {
                    // User has exceeded the max interval, give all rewards
                    rewardIds.addAll(entry.getValue());
                } else if (joinCount >= minJoins) {
                    // User is within interval, give rewards for their current position and below
                    int position = joinCount - minJoins;
                    Iterator<Float> iterator = entry.getValue().iterator();
                    int count = 0;

                    while (iterator.hasNext() && count <= position) {
                        rewardIds.add(iterator.next());
                        count++;
                    }
                }
            }
        }

        // Filter out rewards the user already has
        rewardIds.removeAll(onlineUser.getReceivedRewards());
        rewardIds.removeAll(onlineUser.getRewardsToBeClaimed());
        return rewardIds;
    }

    public void removeReward(JoinStreakReward reward) {
        // Remove from our rewards set
        rewards.remove(reward);

        int rewardId = reward.getId();

        // Remove all instances of this reward from the joinRewardsMap
        for (Map.Entry<Integer, LinkedHashSet<Float>> entry : joinRewardsMap.entrySet()) {
            // Create a set of reward keys to remove
            Set<Float> toRemove = new HashSet<>();

            for (Float rewardKey : entry.getValue()) {
                // If the integer part of this float equals the reward ID, mark for removal
                if ((int)Math.floor(rewardKey) == rewardId) {
                    toRemove.add(rewardKey);
                }
            }

            // Remove all marked reward keys
            entry.getValue().removeAll(toRemove);

            // If the set is now empty, remove the entire entry in a separate step
            // (to avoid ConcurrentModificationException)
        }

        // Remove any empty sets left in the map (in a separate loop to avoid concurrent modification)
        joinRewardsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void updateJoinRewardsMap(JoinStreakReward reward) {
        int rewardId = reward.getId();
        int minJoins = reward.getMinRequiredJoins();
        int maxJoins = reward.getMaxRequiredJoins();

        // First, remove any existing entries for this reward
        joinRewardsMap.entrySet().removeIf(entry -> entry.getKey() == rewardId);

        if (minJoins == -1) {
            joinRewardsMap.put(rewardId, new LinkedHashSet<>());
        }
        // Single value case - add just one entry
        else if (minJoins == maxJoins) {
            joinRewardsMap.put(rewardId, new LinkedHashSet<>(Collections.singleton((float)rewardId)));
        }
        // Range case - add entries for each join count in the range
        else {
            LinkedHashSet<Float> set = new LinkedHashSet<>();
            int range = maxJoins - minJoins;

            for (int joinCount = 0; joinCount <= range; joinCount++) {
                // Use exact decimal string representation
                BigDecimal base = BigDecimal.valueOf(rewardId);
                BigDecimal increment = new BigDecimal("0.1").multiply(BigDecimal.valueOf(joinCount));
                BigDecimal value = base.add(increment);

                // Convert to string with proper scale and then back to float for guaranteed precision
                value = new BigDecimal(value.setScale(1, RoundingMode.HALF_UP).toString());
                set.add(value.floatValue());
            }
            joinRewardsMap.put(rewardId, set);
        }
    }

    public JoinStreakReward getReward(int id) {
        return rewards.stream()
                .filter(g -> g.getId() == id)
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
                    String rewardID = file.getName().replace(".yml", "");
                    JoinStreakReward reward = new JoinStreakReward(plugin, Integer.parseInt(rewardID), -1);
                    addReward(reward);
                }
            }
        }
        plugin.getLogger().info(String.valueOf(joinRewardsMap));


    }

    private void checkRewardsForUser(OnlineUser onlineUser, Player player) {
        // Get the current join streak count
        int currentStreak = onlineUser.getJoinStreak();

        // Get all rewards that match this specific join count
        LinkedHashSet<Float> unclaimedRewards = getRewardIdsForJoinCount(currentStreak, onlineUser);
        // Process each unclaimed reward
        for (Float rewardKey : unclaimedRewards) {
            // Get the base reward ID (integer part)
            int rewardId = (int) Math.floor(rewardKey);
            JoinStreakReward reward = getReward(rewardId);

            if (reward != null) {
                processQualifiedReward(onlineUser, player, reward, rewardKey);
            }
        }
    }

    private void processQualifiedReward(OnlineUser onlineUser, Player player, JoinStreakReward reward, float rewardKey) {
        // Check if player has auto-claim permission
        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {
            // Auto claim the reward with the specific key (not just the integer ID)
            onlineUser.addReceivedReward(rewardKey);
            processCompletedReward(player, reward, rewardKey, false);
        } else {
            // Add to pending rewards with the specific key
            onlineUser.addRewardToBeClaimed(rewardKey);

            // Notify player about pending reward
            Component pendingMessage = Utils.parseColors(plugin.getConfiguration().getJoinClaimMessage());
            player.sendMessage(pendingMessage);
        }
    }

    public void processCompletedReward(Player player, JoinStreakReward reward, float instance, boolean manualClaim) {

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());

        if(instance == 1.0f){
            onlineUser.addReceivedReward(reward.getId());
            onlineUser.removeRewardToBeClaimed(reward.getId());
        }else{
            onlineUser.addReceivedReward(instance);
            onlineUser.removeRewardToBeClaimed(instance);
        }


        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForReward(onlineUser, reward);
        }

        executeRewardCommands(reward, player);

        if(!manualClaim)
            sendRewardMessage(player, reward);

        if(plugin.getConfiguration().getStreakCheckVerbose()){
            String joinRequirement;
            if(reward.isSingleJoinReward()) {
                joinRequirement = String.valueOf(reward.getMinRequiredJoins());
            } else {
                joinRequirement = reward.getMinRequiredJoins() + " to " + reward.getMaxRequiredJoins();
            }
            plugin.getLogger().info(String.format("User %s has received the join streak reward %d which requires %s consecutive joins!",
                    onlineUser.getNickname(), reward.getId(), joinRequirement));
        }

        playRewardSound(player, reward);
    }

    private void assignPermissionsForReward(OnlineUser onlineUser, JoinStreakReward reward) {
        ArrayList<String> permissions = reward.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            try {
                LuckPermsManager.getInstance(plugin).assignRewardPermissions(onlineUser.getUuid(), reward);
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("Failed to assign permissions for join streak reward %d to player %s: %s",
                        reward.getId(), onlineUser.getNickname(), e.getMessage()));
            }
        }
    }

    private void executeRewardCommands(JoinStreakReward reward, Player player) {
        ArrayList<String> commands = reward.getCommands();
        if (commands != null && !commands.isEmpty()) {
            commands.forEach(command -> {
                try {
                    String formattedCommand = formatRewardCommand(command, player, reward);
                    if (plugin.getConfiguration().getStreakCheckVerbose()) {
                        plugin.getLogger().info("Executing command: " + formattedCommand);
                    }
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe(String.format("Failed to execute command for join streak reward %d: %s",
                            reward.getId(), e.getMessage()));
                }
            });
        }
    }

    private String formatRewardCommand(String command, Player player, JoinStreakReward reward) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%PLAYER_NAME%", player.getName());

        return replacePlaceholders(command, replacements).replaceFirst("/", "");
    }

    private void playRewardSound(Player player, JoinStreakReward reward) {
        try {
            String soundName = reward.getRewardSound();
            Sound sound = null;

            try {
                sound = (Sound) Sound.class.getField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                if (plugin.getConfiguration().getGoalsCheckVerbose()) {
                    plugin.getLogger().info("Could not find sound directly, attempting fallback: " + e.getMessage());
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning(String.format("Could not find sound '%s' for reward '%s'",
                        soundName, reward.getId()));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for goal '%s': %s",
                    reward.getRewardSound(), reward.getId(), e.getMessage()));
        }
    }

    private void sendRewardMessage(Player player, JoinStreakReward reward) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%PLAYER_NAME%", player.getName());
        replacements.put("%REQUIRED_JOINS%", reward.getRequiredJoinsDisplay());
        replacements.put("%MIN_JOINS%", String.valueOf(reward.getMinRequiredJoins()));
        replacements.put("%MAX_JOINS%", String.valueOf(reward.getMaxRequiredJoins()));

        player.sendMessage(Utils.parseColors(replacePlaceholders(reward.getRewardMessage(), replacements)));
    }

    private String replacePlaceholders(String input, Map<String, String> replacements) {
        String result = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public Map<Integer, LinkedHashSet<Float>> getJoinRewardsMap(){
        return joinRewardsMap;
    }
}