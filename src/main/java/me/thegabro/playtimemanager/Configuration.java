package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.SimpleDateFormat;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Configuration {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    // File related fields
    private final boolean createIfNotExist, resource;
    private FileConfiguration config;
    private File file;
    private final File path;
    private final String name;

    // Config settings fields - grouped by category
    // General settings
    private String pluginChatPrefix;
    private String datetimeFormat;

    // Playtime settings
    private String playtimeSelfMessage;
    private String playtimeOthersMessage;

    // Goals settings
    private long goalsCheckRate;
    private boolean goalsCheckVerbose;

    // Placeholders settings
    private boolean placeholdersEnableErrors;
    private String placeholdersDefaultMessage;

    // Permissions settings
    private String permissionsManagerPlugin;

    // Streak settings
    private String streakResetSchedule;
    private String streakTimeZone;
    private boolean streakCheckVerbose;
    private String joinClaimMessage;
    private String joinAutoClaimMessage;
    private boolean rewardsScheduleActivation;
    private boolean joinStreakResetActivation;
    private int joinStreakResetMissesAllowed;

    /**
     * Constructor for Configuration
     *
     * @param path             Location of the configuration file
     * @param name             Name of the configuration file (without extension)
     * @param createIfNotExist Whether to create the file if it doesn't exist
     * @param resource         Whether to use plugin resource as default
     */
    public Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reload();
    }

    //-------------------------------------------------------------------------
    // File operations
    //-------------------------------------------------------------------------

    /**
     * Creates the configuration file if it doesn't exist
     */
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
        file = new File(path, name);
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
        updateAllSettings();
    }

    //-------------------------------------------------------------------------
    // Settings update methods
    //-------------------------------------------------------------------------

    /**
     * Updates all configuration settings from the config file
     */
    private void updateAllSettings() {
        updateGeneralSettings();
        updatePlaytimeSettings();
        updateGoalsSettings();
        updatePlaceholdersSettings();
        updatePermissionsSettings();
        updateStreakSettings();
    }

    /**
     * Updates general plugin settings
     */
    private void updateGeneralSettings() {
        // Update plugin prefix
        this.pluginChatPrefix = config.getString("prefix", "[§6PlayTime§eManager§f]§7");

        // Update datetime format
        String configFormat = config.getString("datetime-format", "MMM dd, yyyy HH:mm:ss");
        try {
            new SimpleDateFormat(configFormat);
            this.datetimeFormat = configFormat;
        } catch (IllegalArgumentException e) {
            this.datetimeFormat = "MMM dd, yyyy HH:mm:ss";
            config.set("datetime-format", this.datetimeFormat);
            save();
            plugin.getLogger().warning("Invalid datetime format in config. Resetting to default: " + this.datetimeFormat);
        }
    }

    /**
     * Updates playtime message settings
     */
    private void updatePlaytimeSettings() {
        this.playtimeSelfMessage = config.getString("playtime-self-message",
                "[&6PlayTime&eManager&f]&7 Your playtime is &6%PLAYTIME%");
        this.playtimeOthersMessage = config.getString("playtime-others-message",
                "[&6PlayTime&eManager&f]&7 The playtime of &e%PLAYER_NAME%&7 is &6%PLAYTIME%");
    }

    /**
     * Updates goals settings
     */
    private void updateGoalsSettings() {
        this.goalsCheckRate = config.getLong("goal-check-rate", 900);
        this.goalsCheckVerbose = config.getBoolean("goal-check-verbose", true);
    }

    /**
     * Updates placeholders settings
     */
    private void updatePlaceholdersSettings() {
        this.placeholdersEnableErrors = config.getBoolean("placeholders.enable-errors", false);
        this.placeholdersDefaultMessage = config.getString("placeholders.default-message", "No data");
    }

    /**
     * Updates permissions settings
     */
    private void updatePermissionsSettings() {
        this.permissionsManagerPlugin = config.getString("permissions-manager-plugin", "luckperms");
    }

    /**
     * Updates streak settings
     */
    private void updateStreakSettings() {
        this.joinClaimMessage = config.getString("join-warn-claim-message", "[&6PlayTime&eManager&f]&7 Great job, " +
                "&e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 consecutive times " +
                "and unlocked a new reward! Use &e/claimrewards&7 to collect it!");
        this.joinAutoClaimMessage = config.getString("join-warn-autoclaim-message", "[&6PlayTime&eManager&f]&7 Great job, " +
                "&e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 consecutive times " +
                "and unlocked a new reward! We have automatically claimed it for you!");
        this.streakCheckVerbose = config.getBoolean("streak-check-verbose", true);
        this.streakTimeZone = config.getString("reset-schedule-timezone", "server");
        this.streakResetSchedule = config.getString("streak-reset-schedule", "0 0 * * *");
        this.rewardsScheduleActivation = config.getBoolean("rewards-check-schedule-activation", true);
        this.joinStreakResetActivation = config.getBoolean("reset-joinstreak.enabled", true);
        this.joinStreakResetMissesAllowed = config.getInt("reset-joinstreak.missed-joins", 1);

    }


    //-------------------------------------------------------------------------
    // Getter and setter methods - grouped by category
    //-------------------------------------------------------------------------

    // General settings
    public String getPluginPrefix() {
        return this.pluginChatPrefix;
    }

    public void setPluginChatPrefix(String prefix) {
        this.pluginChatPrefix = prefix;
        config.set("prefix", prefix);
        save();
    }

    public String getDateTimeFormat() {
        return datetimeFormat;
    }

    public void setDateTimeFormat(String format) {
        this.datetimeFormat = format;
        config.set("datetime-format", format);
        save();
    }

    // Playtime settings
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

    // Goals settings
    public long getGoalsCheckRate() {
        return goalsCheckRate;
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

    public void setGoalsCheckVerbose(Boolean verbose) {
        if (verbose != null) {
            this.goalsCheckVerbose = verbose;
            config.set("goal-check-verbose", verbose);
            save();
        }
    }

    // Placeholders settings
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

    // Permissions settings
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

    public String getStreakResetSchedule() {
        return streakResetSchedule;
    }

    public void setStreakResetSchedule(String streakResetSchedule) {
        this.streakResetSchedule = streakResetSchedule;
        config.set("streak-reset-schedule", streakResetSchedule);
        save();
    }

    public String getStreakTimeZone() {
        return streakTimeZone;
    }

    public void setStreakTimeZone(String streakTimeZone) {
        this.streakTimeZone = streakTimeZone;
        config.set("reset-schedule-timezone", streakTimeZone);
        save();
    }

    public boolean getStreakCheckVerbose() {
        return streakCheckVerbose;
    }

    public void setStreakCheckVerbose(Boolean verbose) {
        if (verbose != null) {
            this.streakCheckVerbose = verbose;
            config.set("streak-check-verbose", verbose);
            save();
        }
    }

    public String getJoinClaimMessage() {
        return joinClaimMessage;
    }

    public void setJoinClaimMessage(String autoclaim) {
        if (autoclaim != null) {
            this.joinClaimMessage = autoclaim;
            config.set("join-warn-claim-message", autoclaim);
            save();
        }
    }

    public String getJoinAutoClaimMessage() {
        return joinAutoClaimMessage;
    }

    public void setJoinAutoClaimMessage(String autoclaim) {
        if (autoclaim != null) {
            this.joinAutoClaimMessage = autoclaim;
            config.set("join-warn-autoclaim-message", autoclaim);
            save();
        }
    }

    public boolean getRewardsCheckScheduleActivation(){
        return rewardsScheduleActivation;
    }

    public void setRewardsCheckScheduleActivation(boolean activation){
        this.rewardsScheduleActivation = activation;
        config.set("rewards-check-schedule-activation", activation);
        save();
    }

    public boolean getJoinStreakResetActivation(){
        return this.joinStreakResetActivation;
    }

    public void setJoinStreakResetActivation(boolean activation){
        this.joinStreakResetActivation = activation;
        config.set("reset-joinstreak.enabled", activation);
        save();
    }

    public int getJoinStreakResetMissesAllowed(){
        return this.joinStreakResetMissesAllowed;
    }

    public void setJoinStreakResetMissesAllowed(int missesAllowed){
        this.joinStreakResetMissesAllowed = missesAllowed;
        config.set("reset-joinstreak.missed-joins", missesAllowed);
        save();
    }

}