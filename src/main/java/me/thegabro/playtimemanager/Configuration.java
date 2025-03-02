package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Configuration {

    private final boolean createIfNotExist, resource;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    private FileConfiguration config;
    private File file;
    private final File path;
    private final String name;
    private long goalsCheckRate;
    private boolean goalsCheckVerbose;
    private String playtimeSelfMessage;
    private String playtimeOthersMessage;
    private String permissionsManagerPlugin;
    private String datetimeFormat;
    private String playtimetopLeaderboardFormat;
    private boolean placeholdersEnableErrors;
    private String placeholdersDefaultMessage;
    private String pluginChatPrefix;
    private long streakInterval;
    private String playtimetopHeader;
    private String playtimetopPreviousPageExists;
    private String playtimetopPreviousPageNotExists;
    private String playtimetopPreviousPageOverText;
    private String playtimetopMiddleText;
    private String playtimetopNextPageExists;
    private String playtimetopNextPageNotExists;
    private String playtimetopNextPageOverText;

    public Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reload();
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

    public void reload() {
        reloadFile();
        reloadConfig();
        updateAllSettings();
    }

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

    private void updateAllSettings() {
        // Update goals settings
        this.goalsCheckRate = config.getLong("goal-check-rate");
        this.goalsCheckVerbose = config.getBoolean("goal-check-verbose");

        // Update messages
        this.playtimeSelfMessage = config.getString("playtime-self-message");
        this.playtimeOthersMessage = config.getString("playtime-others-message");

        // Update permissions settings
        this.permissionsManagerPlugin = config.getString("permissions-manager-plugin", "luckperms");

        // Update datetime settings
        String configFormat = config.getString("datetime-format");
        try {
            new java.text.SimpleDateFormat(configFormat);
            this.datetimeFormat = configFormat;
        } catch (IllegalArgumentException e) {
            this.datetimeFormat = "MMM dd, yyyy HH:mm:ss";
            config.set("datetime-format", this.datetimeFormat);
            save();
            plugin.getLogger().warning("Invalid datetime format in config. Resetting to default: " + this.datetimeFormat);
        }

        // Update prefixes settings
        this.pluginChatPrefix = config.getString("prefix", "[§6PlayTime§eManager§f]§7");

        // Update placeholders settings
        this.placeholdersEnableErrors = config.getBoolean("placeholders.enable-errors", false);
        this.placeholdersDefaultMessage = config.getString("placeholders.default-message", "No data");

        // Update playtimetop settings
        this.playtimetopHeader = config.getString("playtimetop.header",
                "[&6PlayTime&eManager&f]&7 Top 100 players - page: %PAGE_NUMBER%");
        this.playtimetopLeaderboardFormat = config.getString("playtimetop.leaderboard-format",
                "&7&l#%POSITION%&r %PREFIX% &e%PLAYER_NAME% &7- &d%PLAYTIME%");
        this.playtimetopPreviousPageExists = config.getString("playtimetop.footer.previous-page.text-if-page-exists", "&6«");
        this.playtimetopPreviousPageNotExists = config.getString("playtimetop.footer.previous-page.text-if-page-not-exists", "&7«");
        this.playtimetopPreviousPageOverText = config.getString("playtimetop.footer.previous-page.over-text",
                "&7Click to go to previous page");
        this.playtimetopMiddleText = config.getString("playtimetop.footer.middle-text",
                "&7Page %PAGE_NUMBER%/%TOTAL_PAGES%");
        this.playtimetopNextPageExists = config.getString("playtimetop.footer.next-page.text-if-page-exists", "&6»");
        this.playtimetopNextPageNotExists = config.getString("playtimetop.footer.next-page.text-if-page-not-exists", "&7»");
        this.playtimetopNextPageOverText = config.getString("playtimetop.footer.next-page.over-text",
                "&7Click to go to next page");

        // Update streak settings
        this.streakInterval = config.getLong("streak-interval", 86400);
    }

    public String getPluginPrefix() {
        return this.pluginChatPrefix;
    }

    public void setPluginChatPrefix(String prefix) {
        this.pluginChatPrefix = prefix;
        config.set("prefix", prefix);
        save();
    }

    public String getPlaytimetopHeader() {
        return playtimetopHeader;
    }

    public void setPlaytimetopHeader(String header) {
        if (header != null) {
            this.playtimetopHeader = header;
            config.set("playtimetop.header", header);
            save();
        }
    }

    public String getPlaytimetopLeaderboardFormat() {
        return playtimetopLeaderboardFormat;
    }

    public void setPlaytimetopLeaderboardFormat(String format) {
        if (format != null) {
            this.playtimetopLeaderboardFormat = format;
            config.set("playtimetop.leaderboard-format", format);
            save();
        }
    }

    public String getPlaytimetopPreviousPageExists() {
        return playtimetopPreviousPageExists;
    }

    public void setPlaytimetopPreviousPageExists(String text) {
        if (text != null) {
            this.playtimetopPreviousPageExists = text;
            config.set("playtimetop.footer.previous-page.text-if-page-exists", text);
            save();
        }
    }

    public String getPlaytimetopPreviousPageNotExists() {
        return playtimetopPreviousPageNotExists;
    }

    public void setPlaytimetopPreviousPageNotExists(String text) {
        if (text != null) {
            this.playtimetopPreviousPageNotExists = text;
            config.set("playtimetop.footer.previous-page.text-if-page-not-exists", text);
            save();
        }
    }

    public String getPlaytimetopPreviousPageOverText() {
        return playtimetopPreviousPageOverText;
    }

    public void setPlaytimetopPreviousPageOverText(String text) {
        if (text != null) {
            this.playtimetopPreviousPageOverText = text;
            config.set("playtimetop.footer.previous-page.over-text", text);
            save();
        }
    }

    public String getPlaytimetopMiddleText() {
        return playtimetopMiddleText;
    }

    public void setPlaytimetopMiddleText(String text) {
        if (text != null) {
            this.playtimetopMiddleText = text;
            config.set("playtimetop.footer.middle-text", text);
            save();
        }
    }

    public String getPlaytimetopNextPageExists() {
        return playtimetopNextPageExists;
    }

    public void setPlaytimetopNextPageExists(String text) {
        if (text != null) {
            this.playtimetopNextPageExists = text;
            config.set("playtimetop.footer.next-page.text-if-page-exists", text);
            save();
        }
    }

    public String getPlaytimetopNextPageNotExists() {
        return playtimetopNextPageNotExists;
    }

    public void setPlaytimetopNextPageNotExists(String text) {
        if (text != null) {
            this.playtimetopNextPageNotExists = text;
            config.set("playtimetop.footer.next-page.text-if-page-not-exists", text);
            save();
        }
    }

    public String getPlaytimetopNextPageOverText() {
        return playtimetopNextPageOverText;
    }

    public void setPlaytimetopNextPageOverText(String text) {
        if (text != null) {
            this.playtimetopNextPageOverText = text;
            config.set("playtimetop.footer.next-page.over-text", text);
            save();
        }
    }

    public boolean isPlaceholdersEnableErrors() {
        return placeholdersEnableErrors;
    }

    public void setPlaceholdersEnableErrors(boolean enableErrors) {
        this.placeholdersEnableErrors = enableErrors;
        config.set("placeholders.enable-errors", enableErrors);
        save();
    }

    public String getPlaceholdersDefaultMessage() {
        return placeholdersDefaultMessage;
    }

    public void setPlaceholdersDefaultMessage(String message) {
        this.placeholdersDefaultMessage = message;
        config.set("placeholders.default-message", message);
        save();
    }

    public long getStreakInterval() {
        return streakInterval;
    }

    public void setStreakInterval(long streakInterval) {
        this.streakInterval = streakInterval;
        config.set("streak-interval", streakInterval);
        save();
    }

    public long getGoalsCheckRate() {
        return goalsCheckRate;
    }

    public String getDateTimeFormat() {
        return datetimeFormat;
    }

    public void setDateTimeFormat(String format) {
        this.datetimeFormat = format;
        config.set("datetime-format", format);
        save();
    }

    public void setGoalsCheckVerbose(Boolean verbose) {
        if (verbose != null) {
            this.goalsCheckVerbose = verbose;
            config.set("goal-check-verbose", verbose);
            save();
        }
    }

    public void setGoalsCheckRate(Long rate) {
        if (rate != null) {
            this.goalsCheckRate = rate;
            config.set("goal-check-rate", rate);
            save();
        }
    }

    public boolean getGoalsCheckVerbose() {
        return goalsCheckVerbose;
    }

    public String getPermissionsManagerPlugin() {
        return permissionsManagerPlugin;
    }

    public void setPermissionsManagerPlugin(String plugin) {
        if (plugin != null) {
            this.permissionsManagerPlugin = plugin.toLowerCase();
            config.set("permissions-manager-plugin", plugin.toLowerCase());
            save();
        }
    }

    public String getPlaytimeSelfMessage() {
        return playtimeSelfMessage;
    }

    public void setPlaytimeSelfMessage(String message) {
        if (message != null) {
            this.playtimeSelfMessage = message;
            config.set("playtime-self-message", message);
            save();
        }
    }

    public String getPlaytimeOthersMessage() {
        return playtimeOthersMessage;
    }

    public void setPlaytimeOthersMessage(String message) {
        if (message != null) {
            this.playtimeOthersMessage = message;
            config.set("playtime-others-message", message);
            save();
        }
    }
}