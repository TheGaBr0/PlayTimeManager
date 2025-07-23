package me.thegabro.playtimemanager.Customizations;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GUIsConfiguration {

    private static GUIsConfiguration instance;
    private PlayTimeManager plugin;
    private FileConfiguration config;
    private File file;

    // Cache to store all configuration values in memory
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();


    private static final String CONFIG_FILENAME = "GUIs-config.yml";
    private static final String CONFIG_PATH = "Customizations/GUIs/";

    // Private constructor to prevent instantiation
    private GUIsConfiguration() {
        // Empty constructor - initialization happens in initialize()
    }

    // Thread-safe singleton instance getter
    public static synchronized GUIsConfiguration getInstance() {
        if (instance == null) {
            instance = new GUIsConfiguration();
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
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }

        // Fallback to config if cache miss or not loaded
        Object value = config.get(path);

        // Cache the value for future use
        if (value != null) {
            configCache.put(path, value);
        }

        return value;
    }

    /**
     * Gets a value from cache if available, otherwise from config with default
     */
    public Object get(String path, Object def) {
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }

        // Fallback to config if cache miss or not loaded
        Object value = config.get(path, def);

        // Cache the value for future use
        if (value != null) {
            configCache.put(path, value);
        }

        return value;
    }

    /**
     * Sets a value in both config and cache
     */
    public void set(String path, Object value) {
        config.set(path, value);

        configCache.put(path, value);

        save();
    }

    // ==================== CONFIG UPDATE SYSTEM ====================

    /**
     * Creates a backup of all current configuration values
     * @return Map containing all current config values with their paths
     */
    public Map<String, Object> createConfigBackup() {
        Map<String, Object> backup = new HashMap<>();

        // Read all keys from current config
        Set<String> keys = config.getKeys(true);
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                backup.put(key, value);
            }
        }

        plugin.getLogger().info(String.valueOf(backup));

        return backup;
    }

    /**
     * Restores values from a backup, preserving user customizations
     * Only restores leaf values (not entire sections) to preserve new keys
     * @param backup Map containing the backed up values
     */
    public void restoreFromBackup(Map<String, Object> backup) {
        if (backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("Backup is null or empty, nothing to restore");
            return;
        }

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object backupValue = entry.getValue();

            // Check if the key exists in the new config structure
            if (!config.contains(key)) {
                continue;
            }

            // CRITICAL: Skip MemorySection objects (nested sections)
            // These contain multiple keys and restoring them would overwrite entire sections
            if (backupValue instanceof org.bukkit.configuration.MemorySection) {
                continue;
            }

            // Get the current value from the new config (this is the default from the new file)
            Object currentValue = config.get(key);

            // Only restore leaf values (strings, numbers, booleans, lists, etc.)
            // Only restore if:
            // 1. The backup has a non-null value
            // 2. The backup value is different from the current default
            // 3. The backup value is not empty for strings
            if (backupValue != null && !backupValue.equals(currentValue)) {
                // Additional check for strings - don't restore empty strings
                if (backupValue instanceof String && ((String) backupValue).trim().isEmpty()) {
                    continue;
                }

                config.set(key, backupValue);
                configCache.put(key, backupValue);

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

            // Step 3: Save the new resource file
            plugin.saveResource(CONFIG_PATH + CONFIG_FILENAME, true);

            // Step 4: Clear cache and reload the new config
            configCache.clear();
            reloadFile();
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
     * Gets a String List value from cache
     */
    public java.util.List<String> getStringList(String path) {
        Object value = get(path);
        if (value instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> list = (java.util.List<String>) value;
            return list;
        }
        return null;
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
     * Clears the cache
     */
    public void clearCache() {
        configCache.clear();
    }

}