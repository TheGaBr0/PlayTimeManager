package me.thegabro.playtimemanager.Goals;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.quartz.CronExpression;


import java.io.File;
import java.io.IOException;
import java.util.*;

public class Goal {
    // Fields
    private final PlayTimeManager plugin;
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private String name;
    protected final File goalFile;
    private final GoalRewardRequirement requirements;
    private ArrayList<String> rewardPermissions = new ArrayList<>();
    private ArrayList<String> rewardCommands = new ArrayList<>();
    private String goalMessage;
    private String goalSound;
    private boolean active;
    private boolean isRepeatable;
    private String completionCheckInterval;
    private String checkTimeTimezone;
    private boolean isVerbose;
    private CronExpression cronExpression;
    private String checkTimeToText;
    private TimeZone timezone;
    private final DatabaseHandler db = DatabaseHandler.getInstance();
    private BukkitTask intervalTask;
    private Date nextIntervalCheckCron;
    private final Map<String, String> goalMessageReplacements;
    private Date nextIntervalCheck; // Track next check time for interval mode


    private boolean useCronExpression = true; // true for cron, false for seconds
    private long intervalSeconds = 900; // default 15 minutes in seconds

    public Goal(PlayTimeManager plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");
        this.requirements = new GoalRewardRequirement();
        this.goalMessageReplacements = new HashMap<>();
        loadFromFile();
        validateConfiguration();
    }

    public Goal(PlayTimeManager plugin, String name, boolean active) {
        this.plugin = plugin;
        this.name = name;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");

        this.requirements = new GoalRewardRequirement();
        this.goalMessageReplacements = new HashMap<>();

        this.active = active;

        loadFromFile();
        validateConfiguration();
        saveToFile();
    }

    private void validateConfiguration() {
        try {
            parseCheckTimeInterval(completionCheckInterval);

            if ("utc".equalsIgnoreCase(checkTimeTimezone)) {
                this.timezone = TimeZone.getTimeZone("UTC");
            } else {
                this.timezone = TimeZone.getDefault();
            }

            if (useCronExpression && cronExpression != null) {
                this.cronExpression.setTimeZone(timezone);
            }
            saveToFile();

        } catch(Exception e) {
            plugin.getLogger().severe("Invalid check-time-interval configuration in goal " + name + "! Setting the goal as inactive: " + e.getMessage());
            setActivation(false);
        }
    }

    private void parseCheckTimeInterval(String interval) throws Exception {
        if (interval == null || interval.trim().isEmpty()) {
            interval = "900"; // default 900 seconds (15 minutes)
        }
        try {
            // Try to parse as a long (seconds)
            long seconds = Long.parseLong(interval.trim());
            if (seconds <= 0) {
                throw new IllegalArgumentException("Interval in seconds must be positive");
            }
            this.intervalSeconds = seconds;
            this.useCronExpression = false;
            this.cronExpression = null;

            if (isVerbose) {
                plugin.getLogger().info("Goal " + name + " using interval mode: " + seconds + " seconds");
            }

        } catch (NumberFormatException e) {
            // Not a number, try parsing as cron expression
            this.cronExpression = new CronExpression(interval);
            this.useCronExpression = true;
            if (isVerbose) {
                plugin.getLogger().info("Goal " + name + " using cron mode: " + interval);
            }
        }

        translateCheckTimeToText();
    }

    // Core file operations
    protected void loadFromFile() {
        if (goalFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(goalFile);
            requirements.setTime(config.getLong("requirements.time", Long.MAX_VALUE));
            requirements.setPermissions(new ArrayList<>(config.getStringList("requirements.permissions")));
            requirements.setPlaceholderConditions(new ArrayList<>(config.getStringList("requirements.placeholders")));
            goalMessage = config.getString("goal-message", getDefaultGoalMessage());
            goalSound = config.getString("goal-sound", getDefaultGoalSound());
            rewardPermissions = new ArrayList<>(config.getStringList("rewards.permissions"));
            rewardCommands = new ArrayList<>(config.getStringList("rewards.commands"));
            active = config.getBoolean("active", false);
            isRepeatable = config.getBoolean("repeatable", false);
            completionCheckInterval = config.getString("check-time-interval", "900");
            checkTimeTimezone = config.getString("check-time-timezone", "server");
            isVerbose = config.getBoolean("verbose", false);
        } else {
            goalMessage = getDefaultGoalMessage();
            goalSound = getDefaultGoalSound();
            rewardPermissions = new ArrayList<>();
            rewardCommands = new ArrayList<>();
            isRepeatable = false;
            completionCheckInterval = "900";
            checkTimeTimezone = "server";
            isVerbose = false;
        }
    }

    protected void saveToFile() {
        try {
            if (!goalFile.exists()) {
                goalFile.getParentFile().mkdirs();
                goalFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();
            config.options().setHeader(Arrays.asList(
                    "active:",
                    "  - Determines whether this goal is enabled and tracked by the plugin.",
                    "  - Set to 'true' to enable the goal and track player progress.",
                    "  - Set to 'false' (default) to disable the goal without deleting it.",
                    "",
                    "repeatable:",
                    "  - Determines whether the goal can be completed multiple times by a player.",
                    "  - Set to 'true' if players should be able to complete it again after finishing.",
                    "  - Set to 'false' if it should only be completed once.",
                    "",
                    "check-time-interval:",
                    "  - Defines the completion check time rate for the goal.",
                    "  - Can be set in two formats:",
                    "",
                    "  FORMAT 1 - SECONDS (number only):",
                    "    - Enter a number representing seconds (e.g., 900 for 15 minutes)",
                    "    - The check will start immediately after server reload/restart",
                    "    - Subsequent checks will occur every X seconds from the first check",
                    "    - Examples: 300 (5 minutes), 900 (15 minutes), 3600 (1 hour)",
                    "",
                    "  FORMAT 2 - CRON EXPRESSION:",
                    "    - Format: <seconds> <minute> <hour> <day-of-month> <month> <day-of-week>",
                    "    - Checks occur at fixed times regardless of server restarts",
                    "    - I suggest using this website: https://www.freeformatter.com/cron-expression-generator-quartz.html",
                    "    - ChatGPT is also helpful - use the keyword 'quartz cron format'",
                    "    - Examples:",
                    "      '0 */15 * * * ?' - Every 15 minutes at fixed times (e.g., :00, :15, :30, :45)",
                    "      '0 0 0 * * ?' - Daily at midnight (00:00)",
                    "      '0 0 0 ? * MON' - Weekly on Monday at midnight",
                    "      '0 0 0 1 * ?' - Monthly on the 1st at midnight",
                    "",
                    "check-time-timezone:",
                    "  - Whether to use server timezone or UTC for cron scheduling",
                    "  - Values: 'server' or 'utc'",
                    "  - Only applies to cron expressions, not seconds format",
                    "",
                    "verbose:",
                    "  - Enable or disable debug logging in the console for this goal",
                    "",
                    "goal-sound:",
                    "  - The sound played when a player reaches this goal.",
                    "  - A list of available sounds can be found here:",
                    "    https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                    "  - Replace '<VERSION>' with your server's Minecraft version.",
                    "  - N.B. If your current version doesn’t work, use the latest patch of the same major version. ",
                    "    E.g. '1.19' doesn't work, use '1.19.4'.",
                    "",
                    "goal-message:",
                    "  - The message shown to the player when they complete this goal.",
                    "  - Available placeholders:",
                    "    %TIME_REQUIRED% → The time needed to complete the goal.",
                    "    %PLAYER_NAME%   → The player's name.",
                    "    %GOAL_NAME%     → The name of the goal.",
                    "",
                    "requirements.time:",
                    "  - Required playtime (in seconds) for the goal to be completed.",
                    "  - If not set, it defaults to a very large number (intended behavior).",
                    "",
                    "requirements.permissions:",
                    "  - A list of permissions the player must have to complete this goal.",
                    "",
                    "requirements.placeholders:",
                    "  - A list of placeholder conditions that must be met to complete this goal.",
                    "",
                    "rewards.permissions:",
                    "  - Permissions that will be granted to the player when they complete the goal.",
                    "",
                    "rewards.commands:",
                    "  - Commands that will be executed when the player completes the goal.",
                    "  - Available placeholders in commands:",
                    "    PLAYER_NAME → The player's name.",
                    ""
            ));
            config.set("active", active);
            config.set("repeatable", isRepeatable);
            config.set("check-time-interval", completionCheckInterval);
            config.set("check-time-timezone", checkTimeTimezone);
            config.set("verbose", isVerbose);
            config.set("goal-sound", goalSound);
            config.set("goal-message", goalMessage);
            config.set("requirements.time", requirements.getTime());
            config.set("requirements.permissions", requirements.getPermissions());
            config.set("requirements.placeholders", requirements.getPlaceholderConditions());
            config.set("rewards.permissions", rewardPermissions);
            config.set("rewards.commands", rewardCommands);
            config.save(goalFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save goal file for " + name + ": " + e.getMessage());
        }
    }

    public void deleteFile() {
        if (goalFile.exists()) {
            if (!goalFile.delete()) {
                plugin.getLogger().warning("Failed to delete goal file for " + name);
            }
        }
    }

    public void cancelCheckTask() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
    }

    public void startCheckTask() {
        if (useCronExpression) {
            startCronTask();
        } else {
            startIntervalTask();
        }
    }

    private void startCronTask() {
        scheduleNextReset();
        if (isVerbose) {
            Map<String, Object> scheduleInfo = getNextSchedule();
            plugin.getLogger().info(String.format("Next Goal %s completion check " +
                    "occur in %s on %s", name, scheduleInfo.get("timeRemaining"), scheduleInfo.get("nextCheck")));
        }
    }

    private void startIntervalTask() {

        long delayInTicks = intervalSeconds * 20L;

        // Set the initial next check time
        nextIntervalCheck = new Date(System.currentTimeMillis() + (intervalSeconds * 1000));

        if (isVerbose && intervalTask == null) {
            Map<String, Object> scheduleInfo = getNextSchedule();
            plugin.getLogger().info(String.format("Next Goal %s completion check will " +
                    "occur in %s on %s", name, scheduleInfo.get("timeRemaining"), scheduleInfo.get("nextCheck")));
        }

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
                    if (onlineUser != null) {
                        checkCompletion(onlineUser, player);
                    }
                }

                // Update next check time AFTER completing the check
                nextIntervalCheck = new Date(System.currentTimeMillis() + (intervalSeconds * 1000));

                if (isVerbose) {
                    Map<String, Object> scheduleInfo = getNextSchedule();
                    plugin.getLogger().info(String.format("Goal %s check completed, next check will occur in %s on %s",
                            name, scheduleInfo.get("timeRemaining"), scheduleInfo.get("nextCheck")));
                }
            }
        }.runTaskTimer(plugin, delayInTicks, delayInTicks);
    }

    private void scheduleNextReset() {
        cancelCheckTask();

        Date oldSchedule;

        if(nextIntervalCheckCron == null)
            oldSchedule = new Date();
        else
            oldSchedule = nextIntervalCheckCron;

        nextIntervalCheckCron = cronExpression.getNextValidTimeAfter(oldSchedule);

        long delayInMillis = nextIntervalCheckCron.getTime() - oldSchedule.getTime();

        long delayInTicks = Math.max(20, delayInMillis / 50); // Min 1 second (20 ticks)

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
                    if (onlineUser != null) {
                        checkCompletion(onlineUser, player);
                    }
                }

                scheduleNextReset(); // Re-run with the updated time

                if (isVerbose) {
                    Map<String, Object> scheduleInfo = getNextSchedule();
                    plugin.getLogger().info(String.format("Goal %s check completed, will " +
                            "occur in %s on %s", name, scheduleInfo.get("timeRemaining"), scheduleInfo.get("nextCheck")));
                }
            }
        }.runTaskLater(plugin, delayInTicks);
    }

    private void checkCompletion(OnlineUser onlineUser, Player player) {
        if (onlineUser.hasCompletedGoal(name) && !isRepeatable) {
            return;
        }

        if (getRequirements().checkRequirements(player, onlineUser.getPlaytime())) {
            processCompletedGoal(onlineUser, player);
        }
    }

    private void processCompletedGoal(OnlineUser onlineUser, Player player) {
        onlineUser.markGoalAsCompleted(name);

        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForGoal(onlineUser);
        }

        executeCommands(player);
        sendGoalMessage(player);

        if(isVerbose){
            plugin.getLogger().info(String.format("User %s has reached the goal %s which requires %s!",
                    onlineUser.getNickname(), name,Utils.ticksToFormattedPlaytime(getRequirements().getTime())));
        }

        playGoalSound(player);
    }

    private void assignPermissionsForGoal(OnlineUser onlineUser) {
        List<String> permissions = rewardPermissions;
        if (permissions != null && !permissions.isEmpty()) {
            try {
                LuckPermsManager.getInstance(plugin).assignGoalPermissions(onlineUser.getUuid(), this);
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("Failed to assign permissions for goal %s to player %s: %s",
                        name, onlineUser.getNickname(), e.getMessage()));
            }
        }
    }

    private void executeCommands(Player player) {
        List<String> commands = rewardCommands;
        if (commands != null && !commands.isEmpty()) {
            commands.forEach(command -> {
                try {
                    String formattedCommand = formatCommand(command, player);
                    if (plugin.getConfiguration().getBoolean("goal-check-verbose")) {
                        plugin.getLogger().info("Executing command: " + formattedCommand);
                    }
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe(String.format("Failed to execute command for goal %s: %s",
                            name, e.getMessage()));
                }
            });
        }
    }

    private String formatCommand(String command, Player player) {
        goalMessageReplacements.put("PLAYER_NAME", player.getName());
        return replacePlaceholders(command).replaceFirst("/", "");
    }

    private void playGoalSound(Player player) {
        try {
            Sound sound = null;

            // Simple direct field access - most efficient when the name matches exactly
            try {
                sound = (Sound) Sound.class.getField(goalSound).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Log the actual error for debugging if verbose is enabled
                if (plugin.getConfiguration().getBoolean("goal-check-verbose")) {
                    plugin.getLogger().info("Could not find sound directly, attempting fallback: " + e.getMessage());
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning(String.format("Could not find sound '%s' for goal '%s'",
                        goalSound, name));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for goal '%s': %s",
                    goalSound, name, e.getMessage()));
        }
    }

    private void sendGoalMessage(Player player) {
        goalMessageReplacements.put("%PLAYER_NAME%", player.getName());
        goalMessageReplacements.put("%TIME_REQUIRED%",
                getRequirements().getTime() != Long.MAX_VALUE ? Utils.ticksToFormattedPlaytime(requirements.getTime()) : "-");
        goalMessageReplacements.put("%GOAL_NAME%", name);
        player.sendMessage(Utils.parseColors(replacePlaceholders(goalMessage)));
    }

    private String replacePlaceholders(String input) {
        String result = input;
        for (Map.Entry<String, String> entry : goalMessageReplacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        goalMessageReplacements.clear();
        return result;
    }

    public void translateCheckTimeToText() {

        if(useCronExpression){
            try {
                CronParser parser = new CronParser(
                        CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
                );

                var cron = parser.parse(this.cronExpression.toString());
                cron.validate();

                CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
                this.checkTimeToText = descriptor.describe(cron);

            } catch (Exception e) {
                this.checkTimeToText = "Invalid Quartz cron expression";
            }
        }else{
            this.checkTimeToText = "every " + Utils.ticksToFormattedPlaytime(intervalSeconds*20L);
        }
    }

    public void rename(String newName) {
        File oldFile = this.goalFile;

        for(OnlineUser user : onlineUsersManager.getOnlineUsersByUUID().values()){
            user.unmarkGoalAsCompleted(name);
            user.markGoalAsCompleted(newName);
        }

        this.name = newName;

        File newFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + newName + ".yml");

        try {
            // Create parent directories if they don't exist
            if (!newFile.getParentFile().exists()) {
                newFile.getParentFile().mkdirs();
            }

            // If old file exists, move it to new location
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    // If rename fails, try to copy and delete
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(oldFile);
                    config.save(newFile);
                    oldFile.delete();
                }
            }

            // Save to ensure all data is current
            saveToFile();

            // Update the name in the database for all users
            //TODO: async
            db.getGoalsDAO().updateGoalName(oldFile.getName().replace(".yml", ""), newName);

            oldFile.delete();

        } catch (IOException e) {
            plugin.getLogger().severe("Could not rename goal file from " + oldFile.getName() + " to " + newFile.getName() + ": " + e.getMessage());
        }
    }

    // Default values
    private String getDefaultGoalSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultGoalMessage() {
        return "[&6PlayTime&eManager&f]&7 Congratulations &e%PLAYER_NAME%&7 you have completed a new goal!";
    }

    // Basic getters
    public String getName() {
        return name;
    }

    public GoalRewardRequirement getRequirements() {
        return requirements;
    }

    public String getGoalMessage() {
        return goalMessage;
    }

    public String getGoalSound() {
        return goalSound;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRepeatable(){ return isRepeatable; }

    public ArrayList<String> getRewardCommands() {
        return rewardCommands;
    }

    public ArrayList<String> getRewardPermissions() {
        return rewardPermissions;
    }

    public Map<String, Object> getNextSchedule() {
        Map<String, Object> scheduleInfo = new HashMap<>();

        if (active) {
            Date now = new Date();
            Date nextCheck = useCronExpression ? nextIntervalCheckCron : nextIntervalCheck;
            long delayInMillis = nextCheck.getTime() - now.getTime();
            long delayInTicks = Math.max(20, delayInMillis / 50);

            scheduleInfo.put("nextCheck", nextCheck);
            scheduleInfo.put("timeRemaining", Utils.ticksToFormattedPlaytime(delayInTicks));
        } else {
            scheduleInfo.put("nextCheck", "-");
            scheduleInfo.put("timeRemaining", "-");

        }
        scheduleInfo.put("timeCheckToText", checkTimeToText);
        return scheduleInfo;
    }

    // Setters and modifiers
    public void setTime(long time) {
        this.requirements.setTime(time);
        saveToFile();
    }

    public void setGoalMessage(String goalMessage) {
        this.goalMessage = goalMessage;
        saveToFile();
    }

    public void setGoalSound(String goalSound) {
        this.goalSound = goalSound;
        saveToFile();
    }

    public void setActivation(boolean activation) {

        this.active = activation;

        if(activation){
            startCheckTask();
            if(isVerbose)
                plugin.getLogger().info("Goal "+name+" has been activated");
        }
        else{
            cancelCheckTask();
            if(isVerbose)
                plugin.getLogger().info("Goal "+name+" has been deactivated");
        }

        saveToFile();
    }

    public void setRepeatable(boolean repeatable){
        this.isRepeatable = repeatable;
        saveToFile();
    }

    public boolean setCheckTime(String checkTime){
        try {
            parseCheckTimeInterval(checkTime);

            if ("utc".equalsIgnoreCase(checkTimeTimezone)) {
                this.timezone = TimeZone.getTimeZone("UTC");
            } else {
                this.timezone = TimeZone.getDefault();
            }

            if (useCronExpression && cronExpression != null) {
                this.cronExpression.setTimeZone(timezone);
            }

            this.completionCheckInterval = checkTime;
            saveToFile();

            return true;
        } catch(Exception e) {
            setActivation(false);
            return false;
        }
    }

    public void addCommand(String command) {
        rewardCommands.add(command);
        saveToFile();
    }

    public void removeCommand(String command) {
        rewardCommands.remove(command);
        saveToFile();
    }

    public void addPermission(String permission) {
        rewardPermissions.add(permission);
        saveToFile();
    }

    public void removePermission(String permission) {
        rewardPermissions.remove(permission);
        saveToFile();
    }

    public void addRequirementPermission(String permission) {
        requirements.addPermission(permission);
        saveToFile();
    }

    public void removeRequirementPermission(String permission) {
        requirements.removePermission(permission);
        saveToFile();
    }

    public void addPlaceholderCondition(String condition) {
        requirements.addPlaceholderCondition(condition);
        saveToFile();
    }

    public void removePlaceholderCondition(String condition) {
        requirements.removePlaceholderCondition(condition);
        saveToFile();
    }

    public void clearPlaceholderConditions(){
        getRequirements().getPlaceholderConditions().clear();
        saveToFile();
    }

    public void clearRequirementPermissions(){
        getRequirements().getPermissions().clear();
        saveToFile();
    }

    public void kill() {
        goalsManager.removeGoal(this);
        DBUsersManager.getInstance().removeGoalFromAllUsers(name);
        deleteFile();
    }

    // New getter methods for the dual time support
    public boolean isUsingCronExpression() {
        return useCronExpression;
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public String getCompletionCheckInterval() {
        return completionCheckInterval;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Goal goal = (Goal) o;
        return Objects.equals(name, goal.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Goal{" +
                "name='" + name + '\'' +
                ", time=" + requirements.getTime() +
                ", active=" + active +
                ", requirementPermissions=" + requirements.getPermissions().size() +
                ", placeholderConditions=" + requirements.getPlaceholderConditions().size() +
                ", rewardPermissions=" + rewardPermissions.size() +
                ", commands=" + rewardCommands.size() +
                ", message='" + goalMessage + '\'' +
                ", sound='" + goalSound + '\'' +
                ", checkMode=" + (useCronExpression ? "cron" : "interval") +
                ", checkValue=" + (useCronExpression ? completionCheckInterval : intervalSeconds + "s") +
                '}';
    }
}