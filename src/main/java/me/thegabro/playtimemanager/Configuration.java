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
        updateGoalsSettings();
        updateMessages();
        updatePermissionsSettings();
        updateDateTimeSettings();
        updatePrefixesSettings();
        updatePlaceholdersSettings();
        updatePlaytimetopSettings();
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

    private void updateMessages(){
        this.playtimeSelfMessage = config.getString("playtime-self-message");
        this.playtimeOthersMessage = config.getString("playtime-others-message");
    }

    private void updateGoalsSettings(){
        this.goalsCheckRate = config.getLong("goal-check-rate");
        this.goalsCheckVerbose = config.getBoolean("goal-check-verbose");
    }

    private void updatePermissionsSettings() {
        this.permissionsManagerPlugin = config.getString("permissions-manager-plugin", "luckperms");
    }


    private void updatePlaceholdersSettings(){
        this.placeholdersEnableErrors = config.getBoolean("placeholders.enable-errors", false);
        this.placeholdersDefaultMessage = config.getString("placeholders.default-message", "No data");
    }

    public String getPluginPrefix(){
        return this.pluginChatPrefix;
    }

    public void setPluginChatPrefix(String prefix){
        this.pluginChatPrefix = prefix;
        config.set("prefix", prefix);
        save();
    }

    private void updatePrefixesSettings(){
        this.pluginChatPrefix = config.getString("prefix", "[§6PlayTime§eManager§f]§7");
    }

    private void updatePlaytimetopSettings() {
        this.playtimetopLeaderboardFormat = config.getString("playtimetop.leaderboard-format",
                "&7&l#%POSITION%&r %PREFIX% &e%PLAYER_NAME% &7- &d%PLAYTIME%");
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

    public long getGoalsCheckRate(){
        return goalsCheckRate;
    }

    private void updateDateTimeSettings() {
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
    }

    public String getDateTimeFormat() {
        return datetimeFormat;
    }

    public void setDateTimeFormat(String format){
        this.datetimeFormat = format;
        config.set("datetime-format", format);
        save();
    }

    public void setGoalsCheckVerbose(Boolean verbose){
        if (verbose != null) {
            config.set("goal-check-verbose", verbose);
            save();
        }
    }

    public void setGoalsCheckRate(Long rate){
        if (rate != null) {
            config.set("goal-check-rate", rate);
            save();
        }
    }

    public boolean getGoalsCheckVerbose(){
        return goalsCheckVerbose;
    }

    public String getPermissionsManagerPlugin() {
        return permissionsManagerPlugin;
    }

    public void setPermissionsManagerPlugin(String plugin) {
        if (plugin != null) {
            config.set("permissions-manager-plugin", plugin.toLowerCase());
            save();
        }
    }

    public String getPlaytimeSelfMessage(){
        return playtimeSelfMessage;
    }

    public void setPlaytimeSelfMessage(String message){
        if (message != null) {
            config.set("playtime-self-message", message);
            save();
        }
    }

    public String getPlaytimeOthersMessage(){
        return playtimeOthersMessage;
    }

    public void setPlaytimeOthersMessage(String message){
        if (message != null) {
            config.set("playtime-others-message", message);
            save();
        }
    }
}
