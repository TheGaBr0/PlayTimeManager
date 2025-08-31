package me.thegabro.playtimemanager.Updates;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UpdateManager {
    private static UpdateManager instance;
    private final PlayTimeManager plugin;private UpdateChecker updateChecker;
    private final DatabaseBackupUtility backupUtility = DatabaseBackupUtility.getInstance();
    private UpdateManager(PlayTimeManager plugin) {
        this.plugin = plugin;
    }
    private String plugin_version;

    public static UpdateManager getInstance(PlayTimeManager plugin) {
        if (instance == null) {
            instance = new UpdateManager(plugin);
        }
        return instance;
    }

    public void initialize() {
        if (plugin.getConfiguration().getBoolean("check-for-updates")) {
            setupUpdateChecker();
        } else {
            plugin.getLogger().info("Update checking is disabled in configuration.");
        }

        try {
            plugin_version = plugin.getPluginMeta().getVersion();
        } catch (NoSuchMethodError e) {
            plugin_version = plugin.getDescription().getVersion(); //for <1.19.4 servers
        }
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(plugin, UpdateCheckSource.HANGAR, "TheGaBr0/PlayTimeManager/Release")
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion())
                .checkEveryXHours(24)
                .onSuccess((commandSenders, latestVersion) -> {
                    updateChecker.setDownloadLink("https://modrinth.com/plugin/playtimemanager/version/" + latestVersion);
                    updateChecker.setChangelogLink("https://modrinth.com/plugin/playtimemanager/version/" + latestVersion);
                })
                .onFail((commandSenders, exception) -> {
                    plugin.getLogger().warning("hangar.papermc.io seems offline, update check has failed");
                })
                .setNotifyOpsOnJoin(true)
                .checkNow();
    }

    public void performVersionUpdate(String currentVersion, String targetVersion) {

        switch (currentVersion) {
            case "3.1":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.1 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.0.4", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version304To31Updater(plugin).performUpgrade();
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.2":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.2 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.3":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.3 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.2.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.4":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.4 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.3.2", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.5":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.5 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version34to341Updater(plugin).performUpgrade();
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.6":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.6 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4.1", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version341to342Updater(plugin).performUpgrade();
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.7":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.7 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.4.2", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version342to35Updater(plugin).performUpgrade();
                new Version351to352Updater(plugin).performUpgrade();
                break;
            case "3.8":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.8 config version detected, starting the update process...");
                backupUtility.createBackup(generateReadmeContent("3.5", plugin_version));
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");
                new Version351to352Updater(plugin).performUpgrade();
                break;
            default:
                plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
                return;
        }


        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + targetVersion);
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