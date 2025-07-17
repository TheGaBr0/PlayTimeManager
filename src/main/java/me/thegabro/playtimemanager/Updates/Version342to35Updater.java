package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Stream;

public class Version342to35Updater {

    private final PlayTimeManager plugin;
    private final SQLite database;
    private CommandsConfiguration commandsConfiguration = CommandsConfiguration.getInstance();
    public Version342to35Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
        backupUtility.createBackup("play_time", generateReadmeContent());
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
                try {
                    Files.createDirectories(targetPath);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create 'Customizations' folder: " + e.getMessage());
                }
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

        String playtimeSelfMessage = Configuration.getInstance().getConfig().getString("playtime-self-message");
        String playtimeOthersMessage = Configuration.getInstance().getConfig().getString("playtime-others-message");
        Configuration.getInstance().updateConfig(false);
        recreateCommandsConfigurationFile(playtimeSelfMessage, playtimeOthersMessage);
    }

    public void recreateCommandsConfigurationFile(String playtimeSelfMessage, String playtimeOthersMessage){

        commandsConfiguration.initialize(plugin);
        commandsConfiguration.updateConfig();

        commandsConfiguration.set("playtime-self-message", playtimeSelfMessage);
        commandsConfiguration.set("playtime-others-message", playtimeOthersMessage);

        commandsConfiguration.reload();
    }

    private String generateReadmeContent() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        StringBuilder readme = new StringBuilder();
        readme.append("PlayTimeManager Database Backup\n");
        readme.append("============================\n\n");
        readme.append("!!! IMPORTANT VERSION UPGRADE NOTICE !!!\n");
        readme.append("=====================================\n");
        readme.append("This backup was automatically created during the upgrade from version 3.4.2 to 3.5\n");
        readme.append("This is a critical backup as the upgrade adds hide leaderboard field.\n\n");

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
        readme.append("Restoring this backup will revert your data to the 3.4.2 format.\n");

        return readme.toString();
    }
}