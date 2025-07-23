package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Configuration {

    private static Configuration instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    // File related fields
    private final boolean createIfNotExist, resource;
    private FileConfiguration config;
    private File file;
    private final File path;
    private final String name;

    // Cache to store all configuration values in memory
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();



    /**
     * Private constructor to prevent direct instantiation
     */
    private Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reload();
    }

    /**
     * Gets the singleton instance of Configuration
     * @param path Directory path for the config file
     * @param name Name of the config file (without .yml extension)
     * @param createIfNotExist Whether to create the file if it doesn't exist
     * @param resource Whether this is a resource file from the plugin jar
     * @return The singleton Configuration instance
     */
    public static Configuration getInstance(File path, String name, boolean createIfNotExist, boolean resource) {
        if (instance == null) {
            synchronized (Configuration.class) {
                if (instance == null) {
                    instance = new Configuration(path, name, createIfNotExist, resource);
                }
            }
        }
        return instance;
    }

    /**
     * Gets the singleton instance of Configuration (must be initialized first)
     * @return The singleton Configuration instance
     * @throws IllegalStateException if getInstance hasn't been called with parameters first
     */
    public static Configuration getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Configuration must be initialized first with getInstance(File, String, boolean, boolean)");
        }
        return instance;
    }

    /**
     * Resets the singleton instance (useful for testing or plugin reloads)
     */
    public static void resetInstance() {
        synchronized (Configuration.class) {
            instance = null;
        }
    }

    //-------------------------------------------------------------------------
    // File operations
    //-------------------------------------------------------------------------

    private void create() {
        if (file == null) {
            reloadFile();
        }
        if (!createIfNotExist || file.exists()) {
            return;
        }
        file.getParentFile().mkdirs();
        if (resource) {
            plugin.saveResource(name, false);
        } else {
            try {
                file.createNewFile();
            } catch (Exception exc) {
                plugin.getLogger().severe(String.valueOf(exc));
            }
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
        file = new File(path, name);
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

    //-------------------------------------------------------------------------
    // Cache access methods
    //-------------------------------------------------------------------------

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
     * Gets a value from cache with default fallback
     */
    public Object get(String path, Object defaultValue) {
        Object value = get(path);
        return value != null ? value : defaultValue;
    }

    /**
     * Sets a value in both config and cache
     */
    public void set(String path, Object value) {
        config.set(path, value);

        configCache.put(path, value);

        save();
    }

    /**
     * Gets a String List value from cache
     */
    public List<String> getStringList(String path) {
        Object value = get(path);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;

            return list;
        }
        return new ArrayList<>();
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
     * Gets a Long value from cache
     */
    public Long getLong(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
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

    // ==================== CONFIG UPDATE SYSTEM ====================

    /**
     * Creates a backup of all current configuration values
     * @return Map containing all current config values with their paths
     */
    public Map<String, Object> createConfigBackup() {
        Map<String, Object> backup = new HashMap<>();

        // Use cache if available, otherwise read from config
        if (!configCache.isEmpty()) {
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

            // Skip system keys that shouldn't be restored
            if (key.equals("config-version")) {
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
            }
        }
    }

    /**
     * Updates the configuration file while preserving user values
     */
    public void updateConfig(boolean restoreRemovedKeys) {
        try {
            //Step 0: fix null lists (without []) otherwise they will be skipped during update.
            fixNullLists();

            // Step 1: Create backup of current values

            Map<String, Object> backup = createConfigBackup();

            // Step 2: Replace the config file
            if (file.exists()) {
                file.delete();
            }

            plugin.saveResource(name, true);

            // Step 3: Clear the cache and reload the new config

            reloadFile();
            reloadConfig();
            configCache.clear();

            // Step 4: Restore user values (only keys that exist in new config)
            restoreFromBackup(backup);

            // Step 5: Handle removed keys if requested
            if (restoreRemovedKeys) {
                restoreRemovedKeys(backup);
            }

            // Step 6: Update config version and save (embedded into set)
            set("config-version", plugin.CURRENT_CONFIG_VERSION);

            // Step 7:  refresh cache
            loadCache();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Restores keys that existed in the old config but don't exist in the new config
     * @param backup Map containing the backed up values from old config
     */
    private void restoreRemovedKeys(Map<String, Object> backup) {
        if (backup == null || backup.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Only restore if the key doesn't exist in the new config
            if (!config.contains(key)) {
                // Skip certain system keys that shouldn't be restored
                if (key.equals("config-version")) {
                    continue;
                }
                config.set(key, value);
                configCache.put(key, value);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Direct access to FileConfiguration for backwards compatibility
    //-------------------------------------------------------------------------

    public FileConfiguration getConfig() {
        return config;
    }

    //-------------------------------------------------------------------------
    // Data fixers
    //-------------------------------------------------------------------------

    public void fixNullLists(){
        fixEmptyPlayersHiddenFromLeaderBoard();
    }

    private void fixEmptyPlayersHiddenFromLeaderBoard(){
        if(getStringList("placeholders.playtime-leaderboard-blacklist") == null){
            set("placeholders.playtime-leaderboard-blacklist", new ArrayList<String>());
        }
    }

}