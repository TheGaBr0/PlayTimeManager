package me.thegabro.playtimemanager.Customizations;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class GUIsConfiguration {

    private static GUIsConfiguration instance;
    private PlayTimeManager plugin;
    private FileConfiguration config;
    private File file;

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

    public void reload() {
        reloadFile();
        reloadConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public Object get(String path) {
        return config.get(path);
    }

    public Object get(String path, Object def) {
        return config.get(path, def);
    }

    public void set(String path, Object value) {
        config.set(path, value);
        save();
    }

}