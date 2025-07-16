package me.thegabro.playtimemanager.Customizations;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CommandsConfiguration {

    private static CommandsConfiguration instance;
    private PlayTimeManager plugin;
    private FileConfiguration config;
    private File file;

    // Cache to store all configuration values in memory
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();

    // Flag to track if cache has been loaded
    private boolean cacheLoaded = false;

    private static final String CONFIG_FILENAME = "commands-config.yml";
    private static final String CONFIG_PATH = "Customizations/Commands/";

    // Private constructor to prevent instantiation
    private CommandsConfiguration() {
        // Empty constructor - initialization happens in initialize()
    }

    // Thread-safe singleton instance getter
    public static synchronized CommandsConfiguration getInstance() {
        if (instance == null) {
            instance = new CommandsConfiguration();
        }
        return instance;
    }

    // Initialize the configuration with the plugin instance
    public void initialize(PlayTimeManager plugin) {
        if (this.plugin == null) {
            this.plugin = plugin;
            create();
            reload();
        }
    }

    private void create() {
        if (file == null) {
            reloadFile();
        }

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(CONFIG_PATH + CONFIG_FILENAME, false);
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (Exception exc) {
            plugin.getLogger().severe(String.valueOf(exc));
        }
    }

    private void reloadFile() {
        if (plugin == null) {
            throw new IllegalStateException("Plugin not initialized. Call initialize() first.");
        }
        file = new File(plugin.getDataFolder(), CONFIG_PATH + CONFIG_FILENAME);
    }

    private void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Loads all configuration values into memory cache
     */
    private void loadCache() {
        if (config == null) {
            plugin.getLogger().warning("Config is null, cannot load cache");
            return;
        }

        configCache.clear();

        // Load all keys from the configuration into cache
        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            configCache.put(key, value);
        }

        cacheLoaded = true;
    }

    public void reload() {
        reloadFile();
        reloadConfig();
        loadCache(); // Load cache after reloading config
    }

    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Gets a value from cache if available, otherwise from config
     */
    public Object get(String path) {
        if (cacheLoaded && configCache.containsKey(path)) {
            return configCache.get(path);
        }

        // Fallback to config if cache miss or not loaded
        Object value = config.get(path);

        // Cache the value for future use
        if (cacheLoaded && value != null) {
            configCache.put(path, value);
        }

        return value;
    }



    /**
     * Sets a value in both config and cache
     */
    public void set(String path, Object value) {

        config.set(path, value);

        // Update cache
        if (cacheLoaded) {
            configCache.put(path, value);
        }

        save();
    }

    // ==================== CONFIG UPDATE SYSTEM ====================

    /**
     * Creates a backup of all current configuration values
     * @return Map containing all current config values with their paths
     */
    public Map<String, Object> createConfigBackup() {
        Map<String, Object> backup = new HashMap<>();

        // Use cache if available, otherwise read from config
        if (cacheLoaded && !configCache.isEmpty()) {
            backup.putAll(configCache);
        } else {
            // Read all keys from current config
            Set<String> keys = config.getKeys(true);
            for (String key : keys) {
                Object value = config.get(key);
                if (value != null) {
                    backup.put(key, value);
                }
            }
        }

        return backup;
    }

    /**
     * Restores values from a backup, preserving user customizations
     * @param backup Map containing the backed up values
     */
    public void restoreFromBackup(Map<String, Object> backup) {
        if (backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("Backup is null or empty, nothing to restore");
            return;
        }

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Check if the key exists in the new config structure
            if (!config.contains(key)) {
                continue;
            }

            // Set the value in both config and cache
            config.set(key, value);
            if (cacheLoaded) {
                configCache.put(key, value);
            }
        }
    }

    /**
     * Updates the configuration file while preserving user values
     */
    public void updateConfig() {
        try {
            // Step 1: Create backup of current values (only if config exists)
            Map<String, Object> backup = new HashMap<>();
            if (config != null) {
                backup = createConfigBackup();
            }

            // Step 2: Replace the config file
            if (file != null && file.exists()) {
                file.delete();
            }

            // Step 3: Reload the new config
            reloadConfig();

            // Step 4: Restore user values (only if we have a backup)
            if (!backup.isEmpty()) {
                restoreFromBackup(backup);
            }

            // Step 5: Save the updated config and refresh cache
            save();
            loadCache();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Gets a String value from cache
     */
    public String getString(String path) {
        Object value = get(path);
        return value != null ? value.toString() : null;
    }


    /**
     * Gets an Integer value from cache
     */
    public Integer getInt(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }


    /**
     * Gets a Boolean value from cache
     */
    public Boolean getBoolean(String path) {
        Object value = get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }


    /**
     * Gets a Double value from cache
     */
    public Double getDouble(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }


    /**
     * Checks if a key exists in cache
     */
    public boolean contains(String path) {
        if (cacheLoaded) {
            return configCache.containsKey(path);
        }
        return config.contains(path);
    }

    /**
     * Clears the cache
     */
    public void clearCache() {
        configCache.clear();
        cacheLoaded = false;
    }


}