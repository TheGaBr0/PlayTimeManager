package me.thegabro.playtimemanager.Updates;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

public class UpdateManager {
    private static UpdateManager instance;
    private final PlayTimeManager plugin;private UpdateChecker updateChecker;

    private UpdateManager(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public static UpdateManager getInstance(PlayTimeManager plugin) {
        if (instance == null) {
            instance = new UpdateManager(plugin);
        }
        return instance;
    }

    public void initialize() {
        setupUpdateChecker();
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
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.1 config version detected, updating it to the latest one...");
                new Version304To31Updater(plugin).performUpgrade();
                File membro = new File(plugin.getDataFolder()+ File.separator+"Goals"+ File.separator+"membro.yml");
                FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(membro);
                long time = oldConfig.getLong("time", Long.MAX_VALUE);
                plugin.getLogger().info(String.valueOf(time));
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                break;
            case "3.2":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.2 config version detected, updating it to the latest one...");
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                break;
            case "3.3":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.3 config version detected, updating it to the latest one...");
                new Version321to33Updater(plugin).performUpgrade();
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                break;
            case "3.4":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.4 config version detected, updating it to the latest one...");
                new Version332to34Updater(plugin).performUpgrade();
                new Version34to341Updater(plugin).performUpgrade();
                break;
            case "3.5":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.5 config version detected, updating it to the latest one...");
                new Version34to341Updater(plugin).performUpgrade();
                break;
            default:
                plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
                return;
        }


        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + targetVersion);
    }
}