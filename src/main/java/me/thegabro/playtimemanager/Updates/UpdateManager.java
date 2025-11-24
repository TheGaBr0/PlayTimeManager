package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UpdateManager {
    private static UpdateManager instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseBackupUtility backupUtility = DatabaseBackupUtility.getInstance();
    private UpdateManager() {}
    private String plugin_version;

    public static UpdateManager getInstance() {
        if (instance == null) {
            instance = new UpdateManager();
        }
        return instance;
    }

    public void initialize() {

        try {
            plugin_version = plugin.getPluginMeta().getVersion();
        } catch (NoSuchMethodError e) {
            plugin_version = plugin.getDescription().getVersion(); //for <=1.19.4 servers
        }

        if (plugin.getConfiguration().getBoolean("check-for-updates")) {
            setupUpdateChecker();
        } else {
            plugin.getLogger().info("Update checking is disabled in configuration.");
        }
    }

    private void setupUpdateChecker() {
        UpdateChecker updateChecker = new UpdateChecker(
                plugin,
                "playtimemanager",
                "TheGaBr0",
                "PlayTimeManager",
                plugin_version
        );

        // Start checking for updates
        updateChecker.start();
    }

    public boolean performVersionUpdate(String currentVersion, String targetVersion) {
        switch (currentVersion) {
            case "3.1":
                Bukkit.getServer().getConsoleSender().sendMessage(
                        "[§6PlayTime§eManager§f]§c Detected old config version §c3.1§7!"
                );
                Bukkit.getServer().getConsoleSender().sendMessage(
                        "[§6PlayTime§eManager§f]§c This version is no longer supported."
                );
                Bukkit.getServer().getConsoleSender().sendMessage(
                        "[§6PlayTime§eManager§f]§c Please update your plugin to version §e3.5.4 §7first, "
                                + "and then upgrade to the latest version."
                );
                Bukkit.getServer().getConsoleSender().sendMessage(
                        "[§6PlayTime§eManager§f]§c Disabling plugin to prevent issues..."
                );
                return false;
            case "3.2":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.2 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version31to32Updater().performUpgrade();
                new Version321to33Updater().performUpgrade();
                new Version332to34Updater().performUpgrade();
                new Version34to341Updater().performUpgrade();
                new Version341to342Updater().performUpgrade();
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.3":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.3 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.2.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version321to33Updater().performUpgrade();
                new Version332to34Updater().performUpgrade();
                new Version34to341Updater().performUpgrade();
                new Version341to342Updater().performUpgrade();
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.4":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.4 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.3.2", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version332to34Updater().performUpgrade();
                new Version34to341Updater().performUpgrade();
                new Version341to342Updater().performUpgrade();
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.5":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.5 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version34to341Updater().performUpgrade();
                new Version341to342Updater().performUpgrade();
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.6":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.6 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version341to342Updater().performUpgrade();
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.7":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.7 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4.2", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version342to35Updater().performUpgrade();
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.8":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.8 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.5", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version351to352Updater().performUpgrade();
                new Version354to36Updater().performUpgrade();
                break;
            case "3.9":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.9 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.5.4", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version354to36Updater().performUpgrade();
                break;
            case "4.0":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 4.0 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.6", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version36to361Updater().performUpgrade();
                break;
            default:
                plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
                return false;
        }
        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + targetVersion);

        return true;

    }

    private String generateReadmeContent(String currentVersion, String nextVersion) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        return "PlayTimeManager Data Folder Backup\n" +
                "==================================\n\n" +
                "!!! IMPORTANT VERSION UPGRADE NOTICE !!!\n" +
                "========================================\n" +
                "This backup was automatically created during the upgrade from version " +
                currentVersion + " to " + nextVersion + ".\n" +
                "It contains the entire plugin data folder, including configuration and database files.\n" +
                "Use this backup to restore the plugin to its previous state in case of issues.\n\n" +
                "Backup Information:\n" +
                "-------------------\n" +
                "Backup created: " + timestamp + "\n" +
                "\nRestore Instructions:\n" +
                "---------------------\n" +
                "!!! CRITICAL: This will revert ALL PlayTimeManager data and config to version " +
                currentVersion + " !!!\n\n" +
                "Steps to restore:\n" +
                "1. Stop your Minecraft server.\n" +
                "2. Navigate to your server's 'plugins/PlayTimeManager' folder.\n" +
                "4. Delete the entire contents of the 'PlayTimeManager' folder.\n" +
                "5. Extract all files from this backup zip into the 'PlayTimeManager' folder.\n" +
                "6. Start your server.\n\n";
    }
}