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

    private final Map<String, Object> configCache = new ConcurrentHashMap<>();

    private static final String CONFIG_FILENAME = "GUIs-config.yml";
    private static final String CONFIG_PATH = "Customizations/GUIs/";

    private GUIsConfiguration() {}

    public static synchronized GUIsConfiguration getInstance() {
        if (instance == null) {
            instance = new GUIsConfiguration();
        }
        return instance;
    }

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

    private void loadCache() {
        if (config == null) {
            plugin.getLogger().warning("Config is null, cannot load cache");
            return;
        }

        configCache.clear();

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

    public Object get(String path) {
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }

        Object value = config.get(path);

        if (value != null) {
            configCache.put(path, value);
        }

        return value;
    }

    public boolean contains(String path) {
        if (configCache.containsKey(path)) {
            return true;
        }

        return config != null && config.contains(path);
    }

    public Object get(String path, Object def) {
        if (configCache.containsKey(path)) {
            return configCache.get(path);
        }

        Object value = config.get(path, def);

        if (value != null) {
            configCache.put(path, value);
        }

        return value;
    }

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

        configCache.put(path, defaultValue);
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
     * Creates a backup of all current configuration values AND tracks section structure
     * @return Map containing all current config values with their paths
     */
    public Map<String, Object> createConfigBackup() {
        Map<String, Object> backup = new HashMap<>();

        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            if (value != null) {
                backup.put(key, value);
            }
        }

        trackExistingSections(backup, "player-stats-gui.items");
        return backup;
    }

    /**
     * Tracks whether specific configuration sections existed,
     * even if they were empty.
     * This is critical to distinguish between "missing by default"
     * and "intentionally removed by the user".
     * @param backup The backup configuration
     * @param basePath The new default configuration
     */
    private void trackExistingSections(Map<String, Object> backup, String basePath) {
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section == null) return;

        for (String itemName : section.getKeys(false)) {
            String viewsPath = basePath + "." + itemName + ".views";
            ConfigurationSection viewsSection = config.getConfigurationSection(viewsPath);

            if (viewsSection != null) {
                backup.put(viewsPath + ".__EXISTS__", true);
                for (String viewType : viewsSection.getKeys(false)) {
                    backup.put(viewsPath + "." + viewType + ".__EXISTS__", true);
                }
            } else {
                backup.put(viewsPath + ".__MISSING__", true);
            }
        }
    }

    /**
     * Detects sections that were intentionally removed by the user
     * @param backup The backup configuration
     * @param defaultConfig The new default configuration
     * @return Set of paths that should be removed from the new config
     */
    private Set<String> detectRemovedSections(Map<String, Object> backup, FileConfiguration defaultConfig) {
        Set<String> removedPaths = new HashSet<>();

        ConfigurationSection items = defaultConfig.getConfigurationSection("player-stats-gui.items");
        if (items == null) return removedPaths;

        for (String itemName : items.getKeys(false)) {
            String viewsPath = "player-stats-gui.items." + itemName + ".views";
            ConfigurationSection defaultViews = defaultConfig.getConfigurationSection(viewsPath);
            if (defaultViews == null) continue;

            boolean viewsExisted = backup.containsKey(viewsPath + ".__EXISTS__");
            boolean viewsMissing = backup.containsKey(viewsPath + ".__MISSING__");
            if (viewsMissing) continue;

            for (String viewType : defaultViews.getKeys(false)) {
                String fullPath = viewsPath + "." + viewType;
                if (viewsExisted && !backup.containsKey(fullPath + ".__EXISTS__")) {
                    removedPaths.add(fullPath);
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
        if (backup == null || backup.isEmpty()) return;

        Set<String> removedSections = detectRemovedSections(backup, config);

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.endsWith(".__EXISTS__") || key.endsWith(".__MISSING__")) continue;
            if (!config.contains(key)) continue;
            if (value instanceof org.bukkit.configuration.MemorySection) continue;
            if (value instanceof String && ((String) value).trim().isEmpty()) continue;

            if (!Objects.equals(config.get(key), value)) {
                config.set(key, value);
                configCache.put(key, value);
            }
        }

        // Remove sections the user intentionally deleted
        for (String removedPath : removedSections) {
            config.set(removedPath, null);
            configCache.keySet().removeIf(k -> k.startsWith(removedPath));
        }
    }

    /**
     * Replaces the config file with the latest default version
     * while preserving user customizations.
     */
    public void updateConfig() {
        try {
            Map<String, Object> backup = config != null ? createConfigBackup() : new HashMap<>();

            if (file != null && file.exists()) {
                file.delete();
            }

            plugin.saveResource(CONFIG_PATH + CONFIG_FILENAME, true);

            configCache.clear();
            reloadFile();
            reloadConfig();

            if (!backup.isEmpty()) {
                restoreFromBackup(backup);
            }

            save();
            loadCache();

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ConfigurationSection getConfigurationSection(String path) {
        if (config == null) {
            return null;
        }
        return config.getConfigurationSection(path);
    }

    public List<String> getStringList(String path) {
        Object value = get(path);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            return list;
        }
        return null;
    }

    public String getString(String path) {
        Object value = get(path);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public Boolean getBoolean(String path) {
        Object value = get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    public Double getDouble(String path) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    public void clearCache() {
        configCache.clear();
    }
}