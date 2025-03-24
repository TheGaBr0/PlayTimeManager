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
import org.bukkit.command.CommandSender;
import org.quartz.CronExpression;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JoinStreaksManager {
    private static JoinStreaksManager instance;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final Map<Integer, LinkedHashSet<String>> joinRewardsMap = new HashMap<>();
    private final Set<String> joinedDuringCurrentInterval = new HashSet<>();
    private PlayTimeManager plugin;
    private static PlayTimeDatabase db;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private BukkitTask intervalTask;
    private JoinStreakReward lastRewardByJoins;
    private CronExpression cronExpression;
    private TimeZone timezone;
    private Date nextIntervalReset;

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

        validateConfiguration();

        clearRewards();
        loadRewards();
        updateIntervalResetTimes();
        populateJoinedUsers();
        startIntervalTask();
    }

    private void validateConfiguration() {
        try {
            // Validate cron expression (using Quartz cron expression)
            String cronString = plugin.getConfiguration().getStreakResetSchedule();
            this.cronExpression = new CronExpression(cronString);

            // Set the timezone
            String timezoneConfig = plugin.getConfiguration().getStreakTimeZone();
            if ("utc".equalsIgnoreCase(timezoneConfig)) {
                this.timezone = TimeZone.getTimeZone("UTC");
            } else {
                this.timezone = TimeZone.getDefault();
            }
            this.cronExpression.setTimeZone(timezone);

            if(plugin.getConfiguration().getStreakCheckVerbose()) {
                plugin.getLogger().info("Join streak configuration validated successfully");
                plugin.getLogger().info("Using cron schedule: " + cronString);
                plugin.getLogger().info("Using timezone: " + timezone.getID());
            }

        } catch (ParseException e) {
            plugin.getLogger().severe("Invalid cron expression in config! Using default: 0 0 0 * * ?");
            try {
                // Make sure the default is in the correct format for Quartz CronExpression
                this.cronExpression = new CronExpression("0 0 0 * * ?");
                this.timezone = TimeZone.getDefault();
                this.cronExpression.setTimeZone(timezone);
            } catch (ParseException ex) {
                plugin.getLogger().severe("Critical error initializing cron scheduler: " + ex.getMessage());
            }
        }
    }

    private void updateIntervalResetTimes() {
        Date now = new Date();
        // Update next reset time using Quartz Cron Expression
        nextIntervalReset = cronExpression.getNextValidTimeAfter(now);
    }

    private Date getPreviousCronTime(Date fromTime) {
        // Create a working copy of the reference time
        Date workingTime = new Date(fromTime.getTime());

        // Step back one minute to ensure we find the previous execution
        // if the reference time happens to be exactly at an execution time
        Calendar cal = Calendar.getInstance(timezone);
        cal.setTime(workingTime);
        cal.add(Calendar.MINUTE, -1);
        workingTime = cal.getTime();

        // Get the next execution time AFTER our offset working time
        // This actually gives us the first future execution from our offset time
        Date nextTime = cronExpression.getNextValidTimeAfter(workingTime);

        // If we found a next time, step back one more millisecond and find the next time again
        // This will give us the previous execution before the reference time
        if (nextTime != null) {
            cal.setTime(nextTime);
            cal.add(Calendar.MILLISECOND, -1);
            Date previousTime = cronExpression.getNextValidTimeAfter(cal.getTime());

            if (previousTime != null && previousTime.before(fromTime)) {
                return previousTime;
            }
        }

        // Fallback: if something went wrong with the calculation above,
        // return 24 hours ago as a safe default
        cal.setTime(fromTime);
        cal.add(Calendar.DAY_OF_YEAR, -1);

        if (plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().warning("Could not determine previous cron time, defaulting to 24 hours ago.");

        return cal.getTime();
    }

    private void populateJoinedUsers() {
        Date now = new Date();
        Date previousReset = getPreviousCronTime(now);
        long intervalSeconds = (now.getTime() - previousReset.getTime()) / 1000L;

        Set<String> recentPlayers = db.getPlayersWithinTimeInterval(intervalSeconds);

        joinedDuringCurrentInterval.clear();
        joinedDuringCurrentInterval.addAll(recentPlayers);
    }

    private void scheduleNextReset() {
        if (intervalTask != null) {
            intervalTask.cancel();
        }

        if (rewards.isEmpty() && plugin.getConfiguration().getStreakCheckVerbose()) {
            plugin.getLogger().info("No active rewards found, but scheduler will continue running to track absolute join streaks.");
        }

        Date now = new Date();
        // Add a small buffer (e.g., 1 second) to avoid scheduling for a time that's too close
        if (nextIntervalReset.getTime() - now.getTime() < 1000) {
            // Get the next interval after the current nextIntervalReset
            nextIntervalReset = cronExpression.getNextValidTimeAfter(nextIntervalReset);
        }

        long delayInMillis = nextIntervalReset.getTime() - now.getTime();
        long delayInTicks = Math.max(20, delayInMillis / 50); // Min 1 second (20 ticks)

        if (plugin.getConfiguration().getStreakCheckVerbose()) {
            plugin.getLogger().info("Next join streak interval reset scheduled for: " + nextIntervalReset +
                    " (in " + Utils.ticksToFormattedPlaytime(delayInTicks) + ")");
        }

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getConfiguration().getStreakCheckVerbose()) {
                    plugin.getLogger().info("Resetting join streak interval tracking. Cleared " +
                            joinedDuringCurrentInterval.size() + " tracked players.");
                }

                // We should always reset missing player streaks to properly track absolute streaks
                resetMissingPlayerStreaks();
                joinedDuringCurrentInterval.clear();

                // Calculate the next reset time and reschedule
                Date oldNextReset = nextIntervalReset;
                updateIntervalResetTimes();

                // Ensure we don't get stuck in a loop if times are too close
                if (Math.abs(nextIntervalReset.getTime() - oldNextReset.getTime()) < 1000) {
                    nextIntervalReset = cronExpression.getNextValidTimeAfter(nextIntervalReset);
                }

                scheduleNextReset(); // Re-run with the updated time
            }
        }.runTaskLater(plugin, delayInTicks);
    }

    public void startIntervalTask() {
        validateConfiguration();
        updateIntervalResetTimes();
        scheduleNextReset();
    }

    private void resetMissingPlayerStreaks() {
        // Get all players from database who have active streaks
        Set<String> playersWithStreaks = db.getPlayersWithActiveStreaks();

        // Find players with streaks who didn't join during this interval
        Set<String> playersToReset = new HashSet<>(playersWithStreaks);
        playersToReset.removeAll(joinedDuringCurrentInterval);

        // Get the current time
        Date now = new Date();
        Date previousReset = getPreviousCronTime(now);
        long intervalSeconds = (now.getTime() - previousReset.getTime()) / 1000L;

        // Process all players who might need reset
        for (String playerUUID : playersToReset) {
            DBUser user = dbUsersManager.getUserFromUUID(playerUUID);
            if (user != null) {
                // Check if the player was recently online within the interval
                long secondsSinceLastSeen = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();

                // If the player was online recently (within this interval), don't reset their streak
                if (secondsSinceLastSeen <= intervalSeconds) {
                    playersToReset.remove(playerUUID);
                    continue;
                }

                // Otherwise reset their streak
                user.resetJoinStreaks();
            }
        }

        // Handle online players separately (as you're already doing)
        for (OnlineUser onlineUser : onlineUsersManager.getOnlineUsersByUUID().values()) {
            // Remove from reset list
            playersToReset.remove(onlineUser.getUuid());

            // Always increment absolute streak
            onlineUser.incrementAbsoluteJoinStreak();

            // Only increment relative streak and check rewards if schedule is active AND rewards exist
            if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewards.isEmpty()) {
                onlineUser.incrementRelativeJoinStreak();
                checkRewardsForUser(onlineUser, onlineUser.getPlayer());
            }
        }

        if (plugin.getConfiguration().getStreakCheckVerbose())
            plugin.getLogger().info("Reset join streaks for " + playersToReset.size() +
                    " players who missed the current interval");
    }

    public void isItAStreak(OnlineUser user, Player player) {
        String playerUUID = user.getUuid();
        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();

        Date now = new Date();
        Date previousReset = getPreviousCronTime(now);

        long streakIntervalSeconds = (now.getTime() - previousReset.getTime()) / 1000L;

        if (secondsBetween <= streakIntervalSeconds) {
            // Check if player already joined during this interval
            if (!joinedDuringCurrentInterval.contains(playerUUID)) {
                // Always increment absolute join streak regardless of conditions
                user.incrementAbsoluteJoinStreak();

                // Only increment relative streak and process rewards if schedule is active AND rewards exist
                if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewards.isEmpty()) {
                    user.incrementRelativeJoinStreak();
                    joinedDuringCurrentInterval.add(playerUUID);
                    checkRewardsForUser(user, player);
                } else {
                    // Even if we don't increment relative streak, still track this join
                    joinedDuringCurrentInterval.add(playerUUID);

                    if (plugin.getConfiguration().getStreakCheckVerbose()) {
                        String reason = !plugin.getConfiguration().getRewardsCheckScheduleActivation() ?
                                "rewards check schedule is inactive" :
                                "no active rewards configured";
                        plugin.getLogger().info("Not incrementing relative join streak for " +
                                user.getNickname() + " because " + reason);
                    }
                }
            }
        } else {
            // Too much time has passed, reset streak
            user.resetJoinStreaks();
            // Add to tracking set to prevent multiple increments if they join again soon
            joinedDuringCurrentInterval.add(playerUUID);
        }
    }

    public void addReward(JoinStreakReward reward) {
        rewards.add(reward);

        updateJoinRewardsMap(reward);
        updateEndLoopReward();
    }

    public JoinStreakReward getMainInstance(String instance){

        return getReward(Integer.parseInt(instance.split("\\.")[0]));
    }

    public LinkedHashSet<String> getRewardIdsForJoinCount(int joinCount, OnlineUser onlineUser) {
        LinkedHashSet<String> rewardIds = new LinkedHashSet<>();
        for (Map.Entry<Integer, LinkedHashSet<String>> entry : joinRewardsMap.entrySet()) {
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
                    Iterator<String> iterator = entry.getValue().iterator();
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
        rewards.remove(reward);

        if(rewards.isEmpty())
            plugin.getConfiguration().setRewardsCheckScheduleActivation(false);

        joinRewardsMap.remove(reward.getId());

        updateEndLoopReward();
    }

    public void updateJoinRewardsMap(JoinStreakReward reward) {
        String rewardIdString = String.valueOf(reward.getId());
        int minJoins = reward.getMinRequiredJoins();
        int maxJoins = reward.getMaxRequiredJoins();

        // First, remove any existing entries for this reward
        joinRewardsMap.entrySet().removeIf(entry -> entry.getValue().stream()
                .anyMatch(val -> val.split("\\.")[0].equals(rewardIdString)));

        if (minJoins == -1) {
            joinRewardsMap.put(reward.getId(), new LinkedHashSet<>());
        }
        else if (minJoins == maxJoins) {
            joinRewardsMap.put(reward.getId(), new LinkedHashSet<>(Collections.singleton(rewardIdString+".1")));
        }
        else {
            LinkedHashSet<String> set = new LinkedHashSet<>();

            for (int joinCount = minJoins; joinCount <= maxJoins; joinCount++) {
                String joinCountStr = String.valueOf(joinCount);
                String valueStr = rewardIdString + "." + joinCountStr;

                set.add(valueStr);
            }
            joinRewardsMap.put(reward.getId(), set);
        }
    }

    public void updateEndLoopReward() {
        // Find the reward with the highest required joins
        lastRewardByJoins = rewards.stream()
                .filter(reward -> reward.getMinRequiredJoins() != -1) // Exclude rewards with no join requirement
                .max(Comparator.comparingInt(JoinStreakReward::getMaxRequiredJoins))
                .orElse(null);
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
    }

    private void checkRewardsForUser(OnlineUser onlineUser, Player player) {
        // Get the current join streak count
        int currentStreak = onlineUser.getRelativeJoinStreak();

        // Get all rewards that match this specific join count
        LinkedHashSet<String> unclaimedRewards = getRewardIdsForJoinCount(currentStreak, onlineUser);
        // Process each unclaimed reward
        for (String rewardKey : unclaimedRewards) {

            JoinStreakReward mainInstance = getMainInstance(rewardKey);
            if (mainInstance != null) {
                processQualifiedReward(onlineUser, player, mainInstance, rewardKey);
            }
        }
        if(lastRewardByJoins != null){
            if(onlineUser.getRelativeJoinStreak() >= lastRewardByJoins.getMaxRequiredJoins()){
                restartUserJoinStreakRewards(onlineUser);
            }
        }
    }

    public int getNextRewardId() {
        // Find the maximum ID from the rewards set, or 0 if the set is empty
        return rewards.stream()
                .mapToInt(JoinStreakReward::getId)  // Extract the IDs from rewards (as int)
                .max()                              // Find the maximum ID
                .orElse(0) + 1;                     // Default to 0 if empty, then add 1 for the next ID
    }

    private void processQualifiedReward(OnlineUser onlineUser, Player player, JoinStreakReward reward, String rewardKey) {
        // Check if player has auto-claim permission
        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {
            // Auto claim the reward with the specific key (not just the integer ID)
            onlineUser.addReceivedReward(rewardKey);

            sendRewardRelatedMessages(player, reward, rewardKey, plugin.getConfiguration().getJoinAutoClaimMessage());


            processCompletedReward(player, reward, rewardKey);
        } else {
            // Add to pending rewards with the specific key
            onlineUser.addRewardToBeClaimed(rewardKey);

            sendRewardRelatedMessages(player, reward, rewardKey, plugin.getConfiguration().getJoinClaimMessage());
        }
    }

    public void restartUserJoinStreakRewards(OnlineUser onlineUser){
            Set<String> userRewards = onlineUser.getReceivedRewards();
            for(String rewardId : userRewards){
                onlineUser.unreceiveReward(rewardId);
            }
            onlineUser.migrateUnclaimedRewards();
            onlineUser.resetRelativeJoinStreak();
    }

    public void processCompletedReward(Player player, JoinStreakReward reward, String instance) {

        OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());

        onlineUser.unclaimReward(instance);

        //if player ends a cycle of rewards, then he can't have them as unclaimed but can still have them in their
        //inventory ready to be claimed. In this situation let's just process the claim of that reward
        if (!instance.endsWith("R")) {
            onlineUser.addReceivedReward(instance);
        }

        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForReward(onlineUser, reward);
        }

        executeRewardCommands(reward, player);

        sendRewardRelatedMessages(player, reward, instance, reward.getRewardMessage());

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

    private void sendRewardRelatedMessages(Player player, JoinStreakReward reward, String instance, String message) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%PLAYER_NAME%", player.getName());
        replacements.put("%REQUIRED_JOINS%", instance.matches("^\\d+\\.\\d+.*") ?
                instance.replaceAll("^\\d+\\.(\\d+).*", "$1") : "");
        replacements.put("%MIN_JOINS%", String.valueOf(reward.getMinRequiredJoins()));
        replacements.put("%MAX_JOINS%", String.valueOf(reward.getMaxRequiredJoins()));

        player.sendMessage(Utils.parseColors(replacePlaceholders(message, replacements)));
    }

    private String replacePlaceholders(String input, Map<String, String> replacements) {
        String result = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public Map<Integer, LinkedHashSet<String>> getJoinRewardsMap(){
        return joinRewardsMap;
    }

    public boolean toggleJoinStreakCheckSchedule(CommandSender sender) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());

        boolean currentState = plugin.getConfiguration().getRewardsCheckScheduleActivation();
        plugin.getConfiguration().setRewardsCheckScheduleActivation(!currentState);

        if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
            if (rewards.isEmpty()) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " No active rewards found. Join streak check schedule not started."));
                plugin.getConfiguration().setRewardsCheckScheduleActivation(false);
                return false;
            }

            startIntervalTask();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The join streak check schedule has been activated"));

            Map<String, Object> scheduleInfo = getNextSchedule();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " Next join streak interval reset scheduled for: &e" + formatter.format(
                    ((Date)getNextSchedule().get("nextReset")).toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()) +"&7 (in &e" + scheduleInfo.get("timeRemaining") + "&7)" ));
        } else {
            if (intervalTask != null)
                intervalTask.cancel();

            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                    " The join streak check schedule has been deactivated"));
            plugin.getLogger().info("The join streak check schedule has been deactivated from GUI button");
        }
        return true;
    }

    public Map<String, Object> getNextSchedule() {
        updateIntervalResetTimes();

        Map<String, Object> scheduleInfo = new HashMap<>();

        if(plugin.getConfiguration().getRewardsCheckScheduleActivation()){
            scheduleInfo.put("nextReset", nextIntervalReset);
            Date now = new Date();
            long delayInMillis = nextIntervalReset.getTime() - now.getTime();
            long delayInTicks = Math.max(20, delayInMillis / 50);

            scheduleInfo.put("timeRemaining", Utils.ticksToFormattedPlaytime(delayInTicks));
        }
        else{
            scheduleInfo.put("nextReset", null);
            scheduleInfo.put("timeRemaining", "-");
        }

        return scheduleInfo;
    }

    public JoinStreakReward getLastRewardByJoins(){
        return lastRewardByJoins;
    }

}