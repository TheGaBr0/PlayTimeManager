package me.thegabro.playtimemanager.updaters;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class Version304To31Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;
    private final DBUsersManager dbUsersManager;

    public Version304To31Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
        this.dbUsersManager = DBUsersManager.getInstance();
    }

    public void performFullUpgrade() {
        convertGroupsToGoals();
        performDatabaseMigration();

        recreateConfigFile();
        migrateUserGoalData();
    }

    private void performDatabaseMigration() {
        try (Connection connection = database.getSQLConnection()) {
            // Create backup
            DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
            backupUtility.createBackup("play_time", generateReadmeContent());

            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN completed_goals TEXT DEFAULT ''");
            } catch (SQLException e) {
                plugin.getLogger().severe("Database migration failed: " + e.getMessage());
            }

            // Migrate groups table data
            migrateGroupsTableData(connection);
        } catch (SQLException e) {
            plugin.getLogger().severe("Database migration failed: " + e.getMessage());
        }
    }

    private void migrateGroupsTableData(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM groups")) {

            while (rs.next()) {
                String groupName = rs.getString("group_name");
                long requiredTime = rs.getLong("playtime_required");

                // Convert group to goal
                Goal newGoal = new Goal(plugin, groupName, requiredTime, true);
                newGoal.addPermission("group." + groupName);
            }
        } catch (SQLException e) {
            plugin.getLogger().info("Error while migrating groups: " + e);
        }

        // Drop groups table
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS groups");
        }
    }

    private void convertGroupsToGoals() {
        Map<String, Long> existingGroups = database.getAllGroupsData();

        for (Map.Entry<String, Long> groupEntry : existingGroups.entrySet()) {
            String groupName = groupEntry.getKey();
            Long playtime = groupEntry.getValue();

            Goal newGoal = new Goal(plugin, groupName, playtime, true);
            newGoal.addPermission("group." + groupName);
        }
    }

    private void recreateConfigFile() {

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);;

        String playtimeSelfMessage = config.getString("playtime-self-message");
        String playtimeOthersMessage = config.getString("playtime-others-message");

        long goalsCheckRate = config.getLong("luckperms-check-rate");
        boolean goalsCheckVerbose = config.getBoolean("luckperms-check-verbose");

        configFile.delete();

        Configuration newConfig = new Configuration(
                plugin.getDataFolder(),
                "config",
                true,
                true
        );

        newConfig.setPlaytimeSelfMessage(playtimeSelfMessage);
        newConfig.setPlaytimeOthersMessage(playtimeOthersMessage);
        newConfig.setGoalsCheckRate(goalsCheckRate);
        newConfig.setGoalsCheckVerbose(goalsCheckVerbose);
        newConfig.reload();

        plugin.setConfiguration(newConfig);
    }

    private void migrateUserGoalData() {
        for (DBUser user : dbUsersManager.getAllDBUsers()) {
            long userPlaytime = user.getPlaytime();

            for (Goal goal : me.thegabro.playtimemanager.Goals.GoalsManager.getGoals()) {
                if (userPlaytime >= goal.getTime()) {
                    user.markGoalAsCompleted(goal.getName());
                }
            }
        }

        dbUsersManager.clearCache();
    }

    private String generateReadmeContent() {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("============================\n\n");
        readme.append("!!! IMPORTANT VERSION UPGRADE NOTICE !!!\n");
        readme.append("=====================================\n");
        readme.append("This backup was automatically created during the upgrade from version 3.0.4 to 3.1.\n");
        readme.append("This is a critical backup as the upgrade transforms the groups system into goals.\n\n");

        readme.append("Backup Information:\n");
        readme.append("------------------\n");
        readme.append("Backup created: ").append(timestamp).append("\n");

        readme.append("Restore Instructions:\n");
        readme.append("-------------------\n");
        readme.append("!!! CRITICAL: The restored database file MUST be named 'play_time.db' !!!\n");
        readme.append("If the file is not named exactly 'play_time.db', the plugin will not load it.\n\n");
        readme.append("Steps to restore:\n");
        readme.append("1. Stop your server\n");
        readme.append("2. Delete the current 'play_time.db'\n");
        readme.append("3. Extract the database.db file from this backup zip\n");
        readme.append("4. Rename the extracted file to 'play_time.db'\n");
        readme.append("5. Place it in your plugin's data folder\n");
        readme.append("6. Start your server\n\n");

        readme.append("Warning: This backup contains data from before the groups-to-goals transformation.\n");
        readme.append("Restoring this backup will revert your data to the pre-3.1 format.\n");

        return readme.toString();
    }
}