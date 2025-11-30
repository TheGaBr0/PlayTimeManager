package me.thegabro.playtimemanager.Customizations;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
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
        loadCache();
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
     * Checks if a configuration path exists in either cache or config
     * @param path The configuration path to check
     * @return true if the path exists, false otherwise
     */
    public boolean contains(String path) {
        // First check cache for faster lookup
        if (configCache.containsKey(path)) {
            return true;
        }

        // Fallback to config if not in cache
        return config != null && config.contains(path);
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

    // ==================== GET OR DEFAULT OVERWRITE METHODS ====================

    public Object getOrDefault(String path, Object defaultValue) {
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }

        if (config.contains(path)) {
            Object value = config.get(path);
            if (value != null) {
                configCache.put(path, value);
                return value;
            }
        }

        set(path, defaultValue);
        return defaultValue;
    }

    public String getOrDefaultString(String path, String defaultValue) {
        Object value = getOrDefault(path, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }

    public Integer getOrDefaultInt(String path, Integer defaultValue) {
        Object value = getOrDefault(path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public Boolean getOrDefaultBoolean(String path, Boolean defaultValue) {
        Object value = getOrDefault(path, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public Double getOrDefaultDouble(String path, Double defaultValue) {
        Object value = getOrDefault(path, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> getOrDefaultStringList(String path, List<String> defaultValue) {
        Object value = getOrDefault(path, defaultValue);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return defaultValue;
    }

    // ==================== CONFIG UPDATE SYSTEM WITH SECTION REMOVAL DETECTION ====================

    /**
     * Creates a backup of all current configuration values
     * @return Map containing all current config values with their paths
     */
    public Map<String, Object> createConfigBackup() {
        Map<String, Object> backup = new HashMap<>();

        Set<String> keys = config.getKeys(true);
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                backup.put(key, value);
            }
        }

        return backup;
    }

    /**
     * Detects sections that were intentionally removed by the user
     * @param backup The backup configuration
     * @param defaultConfig The new default configuration
     * @return Set of paths that should be removed from the new config
     */
    private Set<String> detectRemovedSections(Map<String, Object> backup, FileConfiguration defaultConfig) {
        Set<String> removedPaths = new HashSet<>();

        // Check for removed view sections under items
        ConfigurationSection itemsSection = defaultConfig.getConfigurationSection("player-stats-gui.items");
        if (itemsSection != null) {
            for (String itemName : itemsSection.getKeys(false)) {
                String viewsPath = "player-stats-gui.items." + itemName + ".views";

                // Check if views section exists in default
                ConfigurationSection defaultViews = defaultConfig.getConfigurationSection(viewsPath);
                if (defaultViews != null) {
                    // Check each view type (owner, player, staff)
                    for (String viewType : defaultViews.getKeys(false)) {
                        String fullViewPath = viewsPath + "." + viewType;

                        // If this view existed in default but doesn't exist in backup (user removed it)
                        boolean existedInBackup = backup.keySet().stream()
                                .anyMatch(key -> key.startsWith(fullViewPath));

                        if (!existedInBackup) {
                            removedPaths.add(fullViewPath);
                            plugin.getLogger().info("Detected removed section: " + fullViewPath);
                        }
                    }
                }
            }
        }

        return removedPaths;
    }

    /**
     * Restores values from a backup, preserving user customizations and removals
     * @param backup Map containing the backed up values
     */
    public void restoreFromBackup(Map<String, Object> backup) {
        if (backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("Backup is null or empty, nothing to restore");
            return;
        }

        // Detect sections that were intentionally removed
        Set<String> removedSections = detectRemovedSections(backup, config);

        int restoredCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object backupValue = entry.getValue();

            // Check if the key exists in the new config structure
            if (!config.contains(key)) {
                skippedCount++;
                continue;
            }

            // Skip MemorySection objects (nested sections)
            if (backupValue instanceof org.bukkit.configuration.MemorySection) {
                continue;
            }

            // Get the current value from the new config
            Object currentValue = config.get(key);

            // Only restore if the backup value differs from the default
            if (backupValue != null && !backupValue.equals(currentValue)) {
                // Skip empty strings
                if (backupValue instanceof String && ((String) backupValue).trim().isEmpty()) {
                    continue;
                }

                config.set(key, backupValue);
                configCache.put(key, backupValue);
                restoredCount++;
            }
        }

        // Remove sections that were intentionally deleted by the user
        for (String removedPath : removedSections) {
            config.set(removedPath, null);

            // Remove all related cache entries
            Iterator<String> cacheIterator = configCache.keySet().iterator();
            while (cacheIterator.hasNext()) {
                String cacheKey = cacheIterator.next();
                if (cacheKey.startsWith(removedPath)) {
                    cacheIterator.remove();
                }
            }
        }
    }

    /**
     * Updates the configuration file while preserving user values and removals
     */
    public void updateConfig() {
        try {
            // Step 1: Create backup of current values
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

            // Step 5: Restore user values and remove intentionally deleted sections
            if (!backup.isEmpty()) {
                restoreFromBackup(backup);
            }

            // Step 6: Save the updated config and refresh cache
            save();
            loadCache();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets a configuration section from the config
     * @param path The path to the configuration section
     * @return ConfigurationSection or null if not found
     */
    public ConfigurationSection getConfigurationSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getConfigurationSection(path);
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