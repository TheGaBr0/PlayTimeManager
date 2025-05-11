package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Version34to341Updater {
    private final PlayTimeManager plugin;

    public Version34to341Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void performUpgrade() {
        recreateGoalsConfigFiles();
        recreateConfigFile();
    }

    private void recreateGoalsConfigFiles() {

        File goalsFolder = new File(plugin.getDataFolder() + File.separator + "Goals");

        if (!goalsFolder.exists() || !goalsFolder.isDirectory()) {
            plugin.getLogger().info("No Goals folder found. Skipping goal configuration update.");
            return;
        }

        File[] goalFiles = goalsFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (goalFiles == null || goalFiles.length == 0) {
            plugin.getLogger().info("No goal configuration files found. Skipping goal configuration update.");
            return;
        }

        for (File oldGoalFile : goalFiles) {
            try {
                String goalPath = oldGoalFile.getPath();
                String fileName = oldGoalFile.getName();

                // Load old configuration
                FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldGoalFile);

                // Extract data from old config
                long time = oldConfig.getLong("time", Long.MAX_VALUE);
                String goalMessage = oldConfig.getString("goal-message", getDefaultGoalMessage());
                String goalSound = oldConfig.getString("goal-sound", getDefaultGoalSound());
                List<String> rewardPermissions = oldConfig.getStringList("permissions");
                List<String> rewardCommands = oldConfig.getStringList("commands");
                boolean active = oldConfig.getBoolean("active", false);

                // Delete the old file
                if (!oldGoalFile.delete()) {
                    plugin.getLogger().warning("Could not delete old goal file: " + fileName);
                    continue; // Skip to next file if delete failed
                }

                // Create new configuration
                FileConfiguration newConfig = new YamlConfiguration();

                // Set header
                newConfig.options().setHeader(Arrays.asList(
                        "GUIDE OF AVAILABLE OPTIONS:",
                        "---------------------------",
                        "goal-sound is played to a player if it reaches the time specified in this config.",
                        "A list of available sounds can be found here: https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                        "Replace '<VERSION>' in the link with your current minecraft version. If it doesn't work try with the ",
                        "latest update of your version (e.g. '1.19' doesn't work and you need to use '1.19.4')",
                        "---------------------------",
                        "goal-message is showed to a player if it reaches the time specified in this config.",
                        "Available placeholders: %TIME_REQUIRED%, %PLAYER_NAME%. %GOAL_NAME%",
                        "---------------------------",
                        "active determines whether this goal is enabled and being checked by the plugin",
                        "Set to 'true' to enable the goal and track player progress",
                        "Set to 'false' (default option) to disable the goal without deleting it",
                        "This is useful for:",
                        "* Temporarily disabling goals without removing them",
                        "* Testing new goals before making them live",
                        "* Managing seasonal or event-specific goals",
                        "---------------------------",
                        "requirements:",
                        "  time: Required playtime (in seconds) for the goal to be completed",
                        "  permissions: List of permissions that the player must have to complete this goal",
                        "  placeholders: List of placeholder conditions that must be met to complete this goal",
                        "---------------------------",
                        "reward:",
                        "  permissions: Permissions that will be granted to a player when they reach this goal",
                        "  commands: List of commands that will be executed when a player reaches this goal",
                        "  Available placeholders in commands: PLAYER_NAME"
                ));

                // Set new configuration values
                newConfig.set("active", active);
                newConfig.set("goal-sound", goalSound);
                newConfig.set("goal-message", goalMessage);
                newConfig.set("requirements.time", time);
                newConfig.set("requirements.permissions", new ArrayList<>());
                newConfig.set("requirements.placeholders", new ArrayList<>());

                // Make sure we preserve the permissions from the old file
                plugin.getLogger().info(String.valueOf(rewardPermissions));
                newConfig.set("rewards.permissions", rewardPermissions);
                newConfig.set("rewards.commands", rewardCommands);

                // Create a new file with the same name
                File newGoalFile = new File(goalPath);
                newConfig.save(newGoalFile);
                plugin.getLogger().info(String.valueOf(newConfig.getStringList("reward.permissions")));

                plugin.getLogger().info("Successfully updated goal configuration file: " + fileName);

            } catch (IOException e) {
                plugin.getLogger().severe("Failed to update goal configuration file " + oldGoalFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void recreateConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String permissionsManagerPlugin = config.getString("permissions-manager-plugin");
        String datetimeFormat = config.getString("datetime-format");
        String prefix = config.getString("prefix");
        String playtimeSelfMessage = config.getString("playtime-self-message");
        String playtimeOthersMessage = config.getString("playtime-others-message");
        long goalsCheckRate = config.getLong("goal-check-rate");
        boolean goalsCheckVerbose = config.getBoolean("goal-check-verbose");
        String streakResetSchedule = config.getString("streak-reset-schedule");
        String streakResetTimezone = config.getString("reset-schedule-timezone");
        boolean rewardsCheckScheduleActivation = config.getBoolean("rewards-check-schedule-activation");
        boolean streakCheckVerbose = config.getBoolean("streak-check-verbose");
        boolean resetJoinStreakEnabled = config.getBoolean("reset-joinstreak.enabled");
        int resetJoinstreakMissedJoins = config.getInt("missed-joins");
        String joinWarnClaimMessage = config.getString("join-warn-claim-message");
        String joinWarnAutoclaimMessage = config.getString("join-warn-autoclaim-message");
        String joinUnclaimedPreviousMessage = config.getString("join-unclaimed-previous-message");
        boolean placeHoldersErrors = config.getBoolean("placeholders.enable-errors");
        String placeHoldersDefaultMSG = config.getString("placeholders.default-message");

        configFile.delete();

        Configuration newConfig = new Configuration(
                plugin.getDataFolder(),
                "config",
                true,
                true
        );

        newConfig.setPermissionsManagerPlugin(permissionsManagerPlugin);
        newConfig.setDateTimeFormat(datetimeFormat);
        newConfig.setPluginChatPrefix(prefix);
        newConfig.setPlaytimeSelfMessage(playtimeSelfMessage);
        newConfig.setPlaytimeOthersMessage(playtimeOthersMessage);
        newConfig.setGoalsCheckRate(goalsCheckRate);
        newConfig.setGoalsCheckVerbose(goalsCheckVerbose);
        newConfig.setStreakResetSchedule(streakResetSchedule);
        newConfig.setStreakTimeZone(streakResetTimezone);
        newConfig.setRewardsCheckScheduleActivation(rewardsCheckScheduleActivation);
        newConfig.setStreakCheckVerbose(streakCheckVerbose);
        newConfig.setJoinStreakResetActivation(resetJoinStreakEnabled);
        newConfig.setJoinStreakResetMissesAllowed(resetJoinstreakMissedJoins);
        newConfig.setJoinClaimMessage(joinWarnClaimMessage);
        newConfig.setJoinAutoClaimMessage(joinWarnAutoclaimMessage);
        newConfig.setJoinCantClaimMessage(joinUnclaimedPreviousMessage);
        newConfig.setPlaceholdersEnableErrors(placeHoldersErrors);
        newConfig.setPlaceholdersDefaultMessage(placeHoldersDefaultMSG);

        newConfig.reload();

        plugin.setConfiguration(newConfig);
    }

    private String getDefaultGoalSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultGoalMessage() {
        return "[&6PlayTime&eManager&f]&7 Congratulations &e%PLAYER_NAME%&7 you have reached &6%TIME_REQUIRED%&7 of playtime!";
    }
}
