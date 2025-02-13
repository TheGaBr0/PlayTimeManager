package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Version321to322Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;

    public Version321to322Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        addFirstJoinColumn();
        recreateConfigFile();
    }

    public void addFirstJoinColumn() {
        DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
        backupUtility.createBackup("play_time", generateReadmeContent());
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the first_join column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN first_join DATETIME DEFAULT NULL");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to alter table: " + e.getMessage());
        }
    }

    private void recreateConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        String playtimeSelfMessage = config.getString("playtime-self-message");
        String playtimeOthersMessage = config.getString("playtime-others-message");

        long goalsCheckRate = config.getLong("goal-check-rate");
        boolean goalsCheckVerbose = config.getBoolean("goal-check-verbose");

        String permissionsManagerPlugin = config.getString("permissions-manager-plugin");
        String datetimeFormat = config.getString("datetime-format");

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
        newConfig.setPermissionsManagerPlugin(permissionsManagerPlugin);
        newConfig.setDateTimeFormat(datetimeFormat);
        newConfig.reload();

        plugin.setConfiguration(newConfig);
    }

    private String generateReadmeContent() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("============================\n\n");
        readme.append("!!! IMPORTANT VERSION UPGRADE NOTICE !!!\n");
        readme.append("=====================================\n");
        readme.append("This backup was automatically created during the upgrade from version 3.2.1 to 3.2.2\n");
        readme.append("This is a critical backup as the upgrade adds a new first join field.\n\n");

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

        readme.append("Warning: This backup contains data from before the data integrity changes.\n");
        readme.append("Restoring this backup will revert your data to the 3.2.1 format.\n");

        return readme.toString();
    }
}