package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class Version341to342Updater {

    private final PlayTimeManager plugin;

    public Version341to342Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void performUpgrade() {
        recreateConfigFile();
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
        int resetJoinstreakMissedJoins = config.getInt("reset-joinstreak.missed-joins");
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

}
