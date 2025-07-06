package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Version342to343Updater {

    private final PlayTimeManager plugin;
    private final SQLite database;

    public Version342to343Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        addHideLeaderboardAttribute();
        recreateConfigFile();
    }

    public void addHideLeaderboardAttribute(){
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the hide_from_leaderboard column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN hide_from_leaderboard BOOLEAN DEFAULT FALSE;");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().severe("Failed to add hide_from_leaderboard column: " + e.getMessage());
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error during hide_from_leaderboard column addition: " + e.getMessage());
        }
    }

    private void recreateConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String permissionsManagerPlugin = config.getString("permissions-manager-plugin");
        String datetimeFormat = config.getString("datetime-format");
        boolean checkForUpdates = config.getBoolean("check-for-updates");
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
        newConfig.setUpdatesCheckActivation(checkForUpdates);
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