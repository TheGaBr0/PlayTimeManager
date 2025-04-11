package me.thegabro.playtimemanager.JoinStreaks;

import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;
import org.quartz.CronExpression;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class JoinStreaksManager {
    private static JoinStreaksManager instance;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final Map<Integer, LinkedHashSet<String>> joinRewardsMap = new HashMap<>();
    private PlayTimeManager plugin;
    private static PlayTimeDatabase db;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private BukkitTask intervalTask;
    private JoinStreakReward lastRewardByJoins;
    private CronExpression cronExpression;
    private TimeZone timezone;
    private Date nextIntervalReset;
    private long exactIntervalSeconds;
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

        File rewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (!rewardsFolder.exists()) {
            rewardsFolder.mkdirs();
        }

        File warningFile = new File(rewardsFolder, "NEVER RENAME FILES IN THIS FOLDER.txt");
        if (!warningFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(warningFile))) {
                writer.write("NEVER RENAME FILES IN THIS FOLDER\n");
                writer.write("--------------------------------------------------\n");
                writer.write("WARNING: The files in this folder are named according to their ID.\n");
                writer.write("Changing the names of these files will cause the configuration files to be missing in the database.\n");
                writer.write("This could result in failures as IDs are used by the plugin, and modifying the file names will break the mapping.\n");
                writer.write("ID values should never be changed by the user.\n");
                writer.write("--------------------------------------------------");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Date now = new Date();
        Date firstTrigger = cronExpression.getNextValidTimeAfter(now);
        Date secondTrigger = cronExpression.getNextValidTimeAfter(firstTrigger);
        long intervalMillis = secondTrigger.getTime() - firstTrigger.getTime();
        exactIntervalSeconds = intervalMillis/1000;

        clearRewards();
        loadRewards();
        updateIntervalResetTimes();
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
        nextIntervalReset = cronExpression.getNextValidTimeAfter(now);
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

                resetMissingPlayerStreaks();

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
        updateIntervalResetTimes();
        scheduleNextReset();
    }

    private void resetMissingPlayerStreaks() {
        if(plugin.getConfiguration().getJoinStreakResetActivation()) {

            Set<String> playersWithStreaks = db.getPlayersWithActiveStreaks();

            // Process all players who might need reset
            int playersReset = 0;
            for (String playerUUID : playersWithStreaks) {
                DBUser user = dbUsersManager.getUserFromUUID(playerUUID);
                if (user != null) {
                    // Check if the player's last seen time is older than the interval
                    LocalDateTime lastSeen = user.getLastSeen();

                    // Null or empty check first
                    if (lastSeen == null) {
                        user.resetJoinStreaks();
                        playersReset++;
                        continue;
                    }

                    // Calculate seconds since last seen
                    long secondsSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now()).getSeconds();

                    // Reset if seconds since last seen is greater than interval
                    if (secondsSinceLastSeen > exactIntervalSeconds * plugin.getConfiguration().getJoinStreakResetMissesAllowed()) {
                        user.resetJoinStreaks();
                        restartUserJoinStreakRewards(user);
                        playersReset++;
                    }
                }
            }

            if (plugin.getConfiguration().getStreakCheckVerbose()) {
                plugin.getLogger().info(String.format("Streak reset for %d players", playersReset));
            }
        }

        // Handle online players separately
        for (OnlineUser onlineUser : onlineUsersManager.getOnlineUsersByUUID().values()) {
            // Always increment absolute streak
            onlineUser.incrementAbsoluteJoinStreak();

            // Only increment relative streak and check rewards if schedule is active AND rewards exist
            if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewards.isEmpty()) {
                onlineUser.incrementRelativeJoinStreak();

                checkRewardsForUser(onlineUser, onlineUser.getPlayer());
            }
        }

    }

    public void isItAStreak(OnlineUser user, Player player) {
        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();


        if (secondsBetween <= exactIntervalSeconds * plugin.getConfiguration().getJoinStreakResetMissesAllowed()) {
            // Always increment absolute join streak
            user.incrementAbsoluteJoinStreak();


            // Only increment relative streak and process rewards if schedule is active AND rewards exist
            if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewards.isEmpty()) {
                user.incrementRelativeJoinStreak();


                checkRewardsForUser(user, player);
            }
        } else {
            // Too much time has passed, reset streak
            if(plugin.getConfiguration().getJoinStreakResetActivation()){
                user.resetJoinStreaks();
            }
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

        // Improved handling of .R duplicates
        Set<String> toRemove = new HashSet<>();
        for (String reward : onlineUser.getRewardsToBeClaimed()) {
            if (reward.endsWith(".R")) {
                String baseId = reward.substring(0, reward.length() - 2);
                String[] parts = baseId.split("\\.");

                // If second digit doesn't match current joinCount, remove the base ID too
                if (parts.length >= 2 && Integer.parseInt(parts[1]) != joinCount) {
                    toRemove.add(baseId);
                }
            } else {
                toRemove.add(reward); // Remove standard rewards that are already to be claimed
            }
        }

        rewardIds.removeAll(toRemove);
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
            joinRewardsMap.put(reward.getId(), new LinkedHashSet<>(Collections.singleton(rewardIdString+"."+minJoins)));
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

            if (onlineUser.getRewardsToBeClaimed().contains(rewardKey + ".R")) {
                sendRewardRelatedMessages(player, mainInstance, rewardKey, plugin.getConfiguration().getJoinCantClaimMessage());
                continue;
            }

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
            //Let's first check that the user doesn't have any unclaimed clones of this reward from a previous cycle
            if(!onlineUser.getRewardsToBeClaimed().contains(rewardKey.concat(".R"))){
                // Add to pending rewards with the specific key
                onlineUser.addRewardToBeClaimed(rewardKey);
            }

            sendRewardRelatedMessages(player, reward, rewardKey, plugin.getConfiguration().getJoinClaimMessage());
        }
    }

    public void restartUserJoinStreakRewards(DBUser onlineUser){
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
        //if (!instance.endsWith("R")) {
            onlineUser.addReceivedReward(instance.replace(".R", ""));
        //}

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
        replacements.put("PLAYER_NAME", player.getName());

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