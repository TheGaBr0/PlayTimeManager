package me.thegabro.playtimemanager.Translations;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Handles configuration for GUI elements in the PlayTimeManager plugin.
 * Provides methods to access GUI settings and manage the configuration file.
 */
public class GUIsConfiguration {

    private final PlayTimeManager plugin;
    private FileConfiguration config;
    private File file;

    private static final String CONFIG_FILENAME = "GUIs-config.yml";
    private static final String CONFIG_PATH = "Translations/GUIs/";

    /**
     * Constructs a new GUIsConfiguration instance.
     *
     * @param plugin The PlayTimeManager plugin instance
     */
    public GUIsConfiguration(PlayTimeManager plugin) {
        this.plugin = plugin;
        create();
        reload();
    }

    /**
     * Creates the configuration file if it doesn't exist
     */
    private void create() {
        if (file == null) {
            reloadFile();
        }

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(CONFIG_PATH+CONFIG_FILENAME, false);
        }
    }

    /**
     * Saves the configuration to file
     */
    private void save() {
        try {
            config.save(file);
        } catch (Exception exc) {
            plugin.getLogger().severe(String.valueOf(exc));
        }
    }

    /**
     * Reloads the file reference
     */
    private void reloadFile() {
        file = new File(plugin.getDataFolder(), CONFIG_PATH + CONFIG_FILENAME);
    }

    /**
     * Reloads the config from file
     */
    private void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Reloads the configuration completely
     */
    public void reload() {
        reloadFile();
        reloadConfig();
    }

    /**
     * Returns the underlying FileConfiguration for direct manipulation.
     *
     * @return The FileConfiguration object
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Retrieves a specific configuration value.
     *
     * @param path The configuration path
     * @return The value at the specified path
     */
    public Object get(String path) {
        return config.get(path);
    }

    /**
     * Retrieves a specific configuration value with a default.
     *
     * @param path The configuration path
     * @param def The default value to return if path is not found
     * @return The value at the specified path or the default value
     */
    public Object get(String path, Object def) {
        return config.get(path, def);
    }

    /**
     * Sets a configuration value and saves the file.
     *
     * @param path The configuration path
     * @param value The value to set
     */
    public void set(String path, Object value) {
        config.set(path, value);
        save();
    }
}