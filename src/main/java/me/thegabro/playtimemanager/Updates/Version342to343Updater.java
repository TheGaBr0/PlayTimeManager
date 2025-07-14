package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.stream.Stream;

public class Version342to343Updater {

    private final PlayTimeManager plugin;
    private final SQLite database;

    public Version342to343Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        addHideLeaderboardAttribute();
        renameCustomizationsFolder();
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

    public void renameCustomizationsFolder(){

        try {

            Path sourcePath = Paths.get(String.valueOf(plugin.getDataFolder()), "Translations");
            Path targetPath = Paths.get(String.valueOf(plugin.getDataFolder()), "Customizations");

            if (Files.exists(sourcePath)) {
                if (Files.exists(targetPath)) {
                    plugin.getLogger().warning("Customizations folder already exists. Deleting it");
                    deleteDirectory(targetPath);
                }

                // Rename the folder
                copyDirectory(sourcePath, targetPath);
                deleteDirectory(sourcePath);
                plugin.getLogger().info("Successfully created 'Customizations' folder from 'Translations'");

            } else {
                plugin.getLogger().severe("Translations folder does not exist. Please create a 'Translations' folder and try again.");
                plugin.onDisable();
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to rename Translations folder: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error while renaming folder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> pathStream = Files.walk(path)) {
                pathStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> pathStream = Files.walk(source)) {
            pathStream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy: " + sourcePath, e);
                }
            });
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