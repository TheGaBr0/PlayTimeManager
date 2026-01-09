package me.thegabro.playtimemanager.Customizations.PlaytimeFormats;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class PlaytimeFormatsConfiguration {

    private static PlaytimeFormatsConfiguration instance;
    private PlayTimeManager plugin;
    private static final String PLAYTIME_FORMATS_PATH = "Customizations/PlaytimeFormats/";
    private static final String DEFAULT_FORMAT_FILENAME = "default.yml";

    public ArrayList<PlaytimeFormat> playtimeFormats;

    private PlaytimeFormatsConfiguration() {
        this.playtimeFormats = new ArrayList<>();
    }

    public static synchronized PlaytimeFormatsConfiguration getInstance() {
        if (instance == null) {
            instance = new PlaytimeFormatsConfiguration();
        }
        return instance;
    }

    public void initialize(PlayTimeManager plugin) {
        if (this.plugin == null) {
            this.plugin = plugin;
            loadAllFormats();
        }
    }

    public static synchronized void reset() {
        instance = null;
    }

    private void loadAllFormats() {
        if (plugin == null) {
            throw new IllegalStateException("Plugin not initialized. Call initialize() first.");
        }

        playtimeFormats.clear();

        File formatsDirectory = new File(plugin.getDataFolder(), PLAYTIME_FORMATS_PATH);

        if (!formatsDirectory.exists()) {
            formatsDirectory.mkdirs();
        }

        try {
            plugin.saveResource("Customizations/PlaytimeFormats/default.yml", true); // true = overwrite
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save default playtime format: " + e.getMessage());
        }

        File[] files = formatsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));

        if (files == null || files.length == 0) {
            plugin.getLogger().info("No playtime format files found in " + PLAYTIME_FORMATS_PATH);
            return;
        }

        for (File file : files) {
            try {
                loadFormatFromFile(file);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load playtime format from file: " + file.getName() + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + playtimeFormats.size() + " playtime format(s)");
    }

    private void loadFormatFromFile(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Use filename (without .yml extension) as the format name
        String formatName = file.getName().replace(".yml", "");

        // Load all the configuration values with defaults and pass them to constructor
        String yearsSingular = config.getString("years-singular", "y");
        String yearsPlural = config.getString("years-plural", "y");
        String monthsSingular = config.getString("months-singular", "mo");
        String monthsPlural = config.getString("months-plural", "mo");
        String weeksSingular = config.getString("weeks-singular", "w");
        String weeksPlural = config.getString("weeks-plural", "w");
        String daysSingular = config.getString("days-singular", "d");
        String daysPlural = config.getString("days-plural", "d");
        String hoursSingular = config.getString("hours-singular", "h");
        String hoursPlural = config.getString("hours-plural", "h");
        String minutesSingular = config.getString("minutes-singular", "m");
        String minutesPlural = config.getString("minutes-plural", "m");
        String secondsSingular = config.getString("seconds-singular", "s");
        String secondsPlural = config.getString("seconds-plural", "s");
        String formatting = config.getString("formatting", "%y%{years}, %d%{days}, %h%{hours}, %m%{minutes}, %s%{seconds}");
        boolean distributeRemovedTime = config.getBoolean("distribute-removed-time", true);
        // Create format instance with all values from YAML
        PlaytimeFormat format = new PlaytimeFormat(file, formatName, yearsSingular, yearsPlural,
                monthsSingular, monthsPlural, weeksSingular, weeksPlural, daysSingular,
                daysPlural, hoursSingular, hoursPlural, minutesSingular,
                minutesPlural, secondsSingular, secondsPlural, formatting, distributeRemovedTime);

        playtimeFormats.add(format);
    }

    public void reload() {
        loadAllFormats();
    }

    public PlaytimeFormat getFormat(String name) {
        for (PlaytimeFormat format : playtimeFormats) {
            if (format.getName().equalsIgnoreCase(name)) {
                return format;
            }
        }
        return null;
    }

    /* Creates a backup of all current configuration values
     * @return Map containing all current config values with their paths
    */
    public Map<String, Object> createConfigBackup(FileConfiguration playtimeFormat) {
        Map<String, Object> backup = new HashMap<>();

        // Read all keys from current config
        Set<String> keys = playtimeFormat.getKeys(true);
        for (String key : keys) {
            Object value = playtimeFormat.get(key);
            if (value != null) {
                backup.put(key, value);
            }
        }

        return backup;
    }

    /**
     * Restores values from a backup, preserving user customizations
     * Only restores leaf values (not entire sections) to preserve new keys
     * @param backup Map containing the backed up values
     */
    public void restoreFromBackup(Map<String, Object> backup, FileConfiguration playtimeFormat) {
        if (backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("Backup is null or empty, nothing to restore");
            return;
        }

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object backupValue = entry.getValue();

            // Check if the key exists in the new config structure
            if (!playtimeFormat.contains(key)) {
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
            Object currentValue = playtimeFormat.get(key);

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
                playtimeFormat.set(key, backupValue);
            }
        }
    }


    public void formatsUpdater(){
        Set<PlaytimeFormat> formatsClone = new HashSet<>(playtimeFormats);
        playtimeFormats.clear();

        File defaultFile = new File(plugin.getDataFolder(), PLAYTIME_FORMATS_PATH + DEFAULT_FORMAT_FILENAME);

        for(PlaytimeFormat f : formatsClone){
            File file = f.getFormatFile();

            // Skip the default file itself
            if (file.getName().equals(DEFAULT_FORMAT_FILENAME)) {
                continue;
            }

            FileConfiguration playtimeFormat = YamlConfiguration.loadConfiguration(file);

            Map<String, Object> backup = createConfigBackup(playtimeFormat);

            // Delete the old format file
            if (file.exists()) {
                file.delete();
            }

            // Copy the default file to this format's location
            if (defaultFile.exists()) {
                try {
                    Files.copy(defaultFile.toPath(), file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Load the new file and restore the backup
            FileConfiguration updatedConfig = YamlConfiguration.loadConfiguration(file);
            restoreFromBackup(backup, updatedConfig);

            try {
                updatedConfig.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        loadAllFormats();
    }

}
