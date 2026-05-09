package me.thegabro.playtimemanager.ExternalPluginSupport;

import me.thegabro.playtimemanager.ExternalPluginSupport.AFKPlus.AFKPlusAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.AntiAFKPlus.AntiAFKPlusAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX.EssentialsAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.Purpur.PurpurAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.genericAFKPlaceholder.AFKPlaceholderManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;

public class AFKManager {

    private static AFKManager instance;

    private PlayTimeManager plugin;
    private boolean afkDetectionConfigured;
    private String configuredAFKPlugin;

    private AFKManager() {}

    public static AFKManager getInstance() {
        if (instance == null) {
            instance = new AFKManager();
        }
        return instance;
    }

    public void initialize(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.afkDetectionConfigured = checkAFKPlugin();
        handlePlaceholderAfkDetectionLoad(afkDetectionConfigured);
    }

    /**
     * Checks which AFK plugin is configured and registers the appropriate hook.
     *
     * @return true if AFK detection was successfully configured
     */
    private boolean checkAFKPlugin() {
        configuredAFKPlugin = plugin.getConfiguration()
                .getString("afk-detection-plugin", "none").toLowerCase();

        switch (configuredAFKPlugin) {
            case "essentials" -> {
                Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
                if (essentials != null && essentials.isEnabled()) {
                    try {
                        EssentialsAFKHook afkHook = EssentialsAFKHook.getInstance();
                        plugin.getServer().getPluginManager().registerEvents(afkHook, plugin);
                        plugin.getLogger().info("Essentials detected! Launching related functions");
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("ERROR: Failed to initialize Essentials API: " + e.getMessage());
                        return false;
                    }
                } else {
                    plugin.getLogger().warning(
                            "Failed to initialize afk detection: Essentials plugin configured but not found! " +
                                    "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                    );
                    return false;
                }
            }
            case "purpur" -> {
                try {
                    Class.forName("org.purpurmc.purpur.event.PlayerAFKEvent");
                    PurpurAFKHook afkHook = PurpurAFKHook.getInstance();
                    plugin.getServer().getPluginManager().registerEvents(afkHook, plugin);
                    plugin.getLogger().info("Purpur AFK detection enabled! Launching related functions");
                    return true;
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().warning(
                            "Failed to initialize afk detection: Purpur configured but server is not running Purpur! " +
                                    "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                    );
                    return false;
                } catch (Exception e) {
                    plugin.getLogger().severe("ERROR: Failed to initialize Purpur AFK detection: " + e.getMessage());
                    return false;
                }
            }
            case "antiafkplus" -> {
                Plugin antiAFKPlus = Bukkit.getPluginManager().getPlugin("AntiAFKPlus");
                if (antiAFKPlus != null && antiAFKPlus.isEnabled()) {
                    try {
                        AntiAFKPlusAFKHook afkHook = AntiAFKPlusAFKHook.getInstance();
                        afkHook.register();
                        plugin.getLogger().info("AntiAFKPlus detected! Launching related functions");
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("ERROR: Failed to initialize AntiAFKPlus API: " + e.getMessage());
                        return false;
                    }
                } else {
                    plugin.getLogger().warning(
                            "Failed to initialize afk detection: AntiAFKPlus plugin configured but not found! " +
                                    "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                    );
                    return false;
                }
            }
            case "afkplus" -> {
                Plugin antiAFKPlus = Bukkit.getPluginManager().getPlugin("AFKPlus");
                if (antiAFKPlus != null && antiAFKPlus.isEnabled()) {
                    try {
                        AFKPlusAFKHook afkHook = AFKPlusAFKHook.getInstance();
                        plugin.getServer().getPluginManager().registerEvents(afkHook, plugin);
                        plugin.getLogger().info("AFKPlus detected! Launching related functions");
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("ERROR: Failed to initialize AFKPlus API: " + e.getMessage());
                        return false;
                    }
                } else {
                    plugin.getLogger().warning(
                            "Failed to initialize afk detection: AFKPlus plugin configured but not found! " +
                                    "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                    );
                    return false;
                }
            }
            case "jetsantiafkpro" -> {
                Plugin jetsAntiAFKPro = Bukkit.getPluginManager().getPlugin("JetsAntiAFKPro");
                if (jetsAntiAFKPro != null && jetsAntiAFKPro.isEnabled()) {
                    try {
                        Class<?> hookClass = Class.forName("me.thegabro.playtimemanager.ExternalPluginSupport.JetsAntiAFKPro.JetsAntiAFKProHook");
                        Object afkHook = hookClass.getMethod("getInstance").invoke(null);
                        hookClass.getMethod("init").invoke(afkHook);
                        plugin.getLogger().info("JetsAntiAFKPro detected! Launching related functions");
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("ERROR: Failed to initialize JetsAntiAFKPro API: " + e.getMessage());
                        return false;
                    }
                } else {
                    plugin.getLogger().warning(
                            "Failed to initialize afk detection: JetsAntiAFKPro plugin configured but not found! " +
                                    "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                    );
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Handles loading of placeholder-based AFK detection.
     * Validates that PAPI is present, no other AFK plugin is active,
     * and that the required config values are supplied.
     *
     * @param afkDetectionConfigured pass the result of checkAFKPlugin() to avoid calling it twice,
     *                               or null to let this method check it internally
     */
    public void handlePlaceholderAfkDetectionLoad(@Nullable Boolean afkDetectionConfigured) {
        if (!isPlaceholderAfkDetectionEnabled()) {
            AFKPlaceholderManager.getInstance().reset();
            return;
        }

        if (afkDetectionConfigured == null) {
            afkDetectionConfigured = checkAFKPlugin();
        }

        if (!plugin.isPlaceholdersAPIConfigured()) {
            plugin.getLogger().warning("PAPI not found, AFK detection by placeholder check is disabled!");
            return;
        }

        if (afkDetectionConfigured) {
            plugin.getLogger().warning(
                    "Both AFK detection plugin and placeholder AFK detection are enabled! " +
                            "Please set AFK detection plugin to 'none' in order to use placeholder AFK detection."
            );
            return;
        }

        String placeholder = getPlaceholderAfkDetectionPlaceholder();
        String afkValue = getPlaceholderAfkDetectionAfkValue();

        if (placeholder == null) {
            plugin.getLogger().warning("Placeholder AFK Detection enabled but placeholder wasn't supplied.");
            return;
        }

        if (afkValue == null) {
            plugin.getLogger().warning("Placeholder AFK Detection enabled but afk value wasn't supplied.");
            return;
        }

        AFKPlaceholderManager.getInstance().start(placeholder, afkValue);
    }

    // --- Getters ---

    public boolean isAfkDetectionConfigured() {
        return afkDetectionConfigured;
    }

    public String getConfiguredAFKPlugin() {
        return configuredAFKPlugin;
    }

    public boolean isPlaceholderAfkDetectionEnabled() {
        return plugin.getConfiguration().getBoolean("placeholder-afk-detection.enabled", false);
    }

    public String getPlaceholderAfkDetectionPlaceholder() {
        return plugin.getConfiguration().getString("placeholder-afk-detection.placeholder", null);
    }

    public String getPlaceholderAfkDetectionAfkValue() {
        return plugin.getConfiguration().getString("placeholder-afk-detection.afk-value", null);
    }
}