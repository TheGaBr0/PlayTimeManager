package me.thegabro.playtimemanager.Updates;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

public class UpdateManager {
    private static UpdateManager instance;
    private final PlayTimeManager plugin;
    private static final String SPIGOT_RESOURCE_ID = "118284";
    private UpdateChecker updateChecker;

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
        checkForUpdates();
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(plugin, UpdateCheckSource.SPIGET, SPIGOT_RESOURCE_ID)
                .setDownloadLink("https://www.spigotmc.org/resources/playtimemanager.118284/")
                .setChangelogLink("https://www.spigotmc.org/resources/playtimemanager.118284/updates")
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion());
    }

    public void checkForUpdates() {
        if (updateChecker != null) {
            updateChecker.checkEveryXHours(24)
                    .checkNow();
        } else {
            plugin.getLogger().warning("Update checker not properly initialized!");
        }
    }

    public void performVersionUpdate(String currentVersion, String targetVersion) {
        switch (currentVersion) {
            case "3.1":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.1 config version detected, updating it to the latest one...");
                new Version304To31Updater(plugin).performUpgrade();
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                break;
            case "3.2":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.2 config version detected, updating it to the latest one...");
                new Version31to32Updater(plugin).performUpgrade();
                new Version321to33Updater(plugin).performUpgrade();
                break;
            case "3.3":
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.3 config version detected, updating it to the latest one...");
                new Version321to33Updater(plugin).performUpgrade();
                break;
            default:
                plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
                return;
        }

        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + targetVersion);
    }

}