package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Database.DatabaseBackupUtility;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;

import java.text.SimpleDateFormat;
import java.util.List;
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

        if (plugin.getConfiguration().getBoolean("check-for-updates", true)) {
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

    /**
     * One upgrade of the chain. {@code configVersion} is the config version it upgrades FROM,
     * {@code pluginVersion} the plugin release that wrote it (for the messages).
     */
    private record UpgradeStep(String configVersion, String pluginVersion, Class<?> updater, Runnable run) {}

    /**
     * Every upgrade, oldest first. Whoever starts at a given config version goes through that step
     * and all the following ones. A new config version means a new step added at the END of this list.
     */
    private static final List<UpgradeStep> CHAIN = List.of(
            new UpgradeStep("3.2", "3.1", Version31to32Updater.class, () -> new Version31to32Updater().performUpgrade()),
            new UpgradeStep("3.3", "3.2.1", Version321to33Updater.class, () -> new Version321to33Updater().performUpgrade()),
            new UpgradeStep("3.4", "3.3.2", Version332to34Updater.class, () -> new Version332to34Updater().performUpgrade()),
            new UpgradeStep("3.5", "3.4", Version34to341Updater.class, () -> new Version34to341Updater().performUpgrade()),
            new UpgradeStep("3.6", "3.4.1", Version341to342Updater.class, () -> new Version341to342Updater().performUpgrade()),
            new UpgradeStep("3.7", "3.4.2", Version342to35Updater.class, () -> new Version342to35Updater().performUpgrade()),
            new UpgradeStep("3.8", "3.5/3.5.1", Version351to352Updater.class, () -> new Version351to352Updater().performUpgrade()),
            new UpgradeStep("3.9", "3.5.4", Version354to36Updater.class, () -> new Version354to36Updater().performUpgrade()),
            new UpgradeStep("4.0", "3.6", Version36to361Updater.class, () -> new Version36to361Updater().performUpgrade()),
            new UpgradeStep("4.1", "3.6.2", Version362to363Updater.class, () -> new Version362to363Updater().performUpgrade()),
            new UpgradeStep("4.2", "3.6.3", Version363to364Updater.class, () -> new Version363to364Updater().performUpgrade()),
            new UpgradeStep("4.3", "3.6.4", Version364to365Updater.class, () -> new Version364to365Updater().performUpgrade()),
            new UpgradeStep("4.4", "3.6.5", Version365to366Updater.class, () -> new Version365to366Updater().performUpgrade()),
            new UpgradeStep("4.5", "3.6.6", Version366to400Updater.class, () -> new Version366to400Updater().performUpgrade())
    );

    private static int indexOfStep(String configVersion) {
        for (int i = 0; i < CHAIN.size(); i++) {
            if (CHAIN.get(i).configVersion().equals(configVersion)) return i;
        }
        return -1;
    }

    /** The upgrades that run, in order, for a server on the given config version (empty if unknown or unsupported). */
    public static List<Class<?>> updatersFrom(String configVersion) {
        int index = configVersion == null ? -1 : indexOfStep(configVersion);
        if (index < 0) return List.of();
        return CHAIN.subList(index, CHAIN.size()).stream().<Class<?>>map(UpgradeStep::updater).toList();
    }

    public boolean performVersionUpdate(String currentVersion, String targetVersion) {
        if ("3.1".equals(currentVersion)) {
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
        }

        int index = indexOfStep(currentVersion);
        if (index < 0) {
            plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
            return false;
        }

        UpgradeStep first = CHAIN.get(index);
        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 " + currentVersion + " config version detected, starting the update process...");
        backupUtility.createBackup("Update from version " + first.pluginVersion() + " to " + plugin_version);
        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Current configuration backed up successfully");

        for (UpgradeStep step : CHAIN.subList(index, CHAIN.size())) {
            step.run().run();
        }

        Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + targetVersion);

        return true;
    }
}
