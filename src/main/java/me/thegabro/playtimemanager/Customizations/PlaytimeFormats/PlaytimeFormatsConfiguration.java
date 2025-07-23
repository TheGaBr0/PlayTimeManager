package me.thegabro.playtimemanager.Customizations.PlaytimeFormats;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlaytimeFormatsConfiguration {

    private static PlaytimeFormatsConfiguration instance;
    private PlayTimeManager plugin;
    private static final String CONFIG_PATH = "Customizations/PlaytimeFormats/";
    
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

        File formatsDirectory = new File(plugin.getDataFolder(), CONFIG_PATH);

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
            plugin.getLogger().info("No playtime format files found in " + CONFIG_PATH);
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
        String daysSingular = config.getString("days-singular", "d");
        String daysPlural = config.getString("days-plural", "d");
        String hoursSingular = config.getString("hours-singular", "h");
        String hoursPlural = config.getString("hours-plural", "h");
        String minutesSingular = config.getString("minutes-singular", "m");
        String minutesPlural = config.getString("minutes-plural", "m");
        String secondsSingular = config.getString("seconds-singular", "s");
        String secondsPlural = config.getString("seconds-plural", "s");
        String formatting = config.getString("formatting", "%y%{years}, %d%{days}, %h%{hours}, %m%{minutes}, %s%{seconds}");

        // Create format instance with all values from YAML
        PlaytimeFormat format = new PlaytimeFormat(formatName, yearsSingular, yearsPlural, daysSingular,
                daysPlural, hoursSingular, hoursPlural, minutesSingular,
                minutesPlural, secondsSingular, secondsPlural, formatting);

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

    public List<String> getFormatNames() {
        List<String> names = new ArrayList<>();
        for (PlaytimeFormat format : playtimeFormats) {
            names.add(format.getName());
        }
        return names;
    }

    public boolean hasFormat(String name) {
        return getFormat(name) != null;
    }

    public int getFormatCount() {
        return playtimeFormats.size();
    }

    public List<PlaytimeFormat> getAllFormats() {
        return new ArrayList<>(playtimeFormats);
    }

}
