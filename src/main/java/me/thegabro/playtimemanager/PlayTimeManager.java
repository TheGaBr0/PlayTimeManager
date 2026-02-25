package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Commands.PlayTimeStats;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.LogFilter;
import me.thegabro.playtimemanager.Database.Migration.DatabaseMigration;
import me.thegabro.playtimemanager.ExternalPluginSupport.AntiAFKPlus.AntiAFKPlusAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.EssentialsX.EssentialsAFKHook;
import me.thegabro.playtimemanager.ExternalPluginSupport.Purpur.PurpurAFKHook;
import me.thegabro.playtimemanager.GUIs.Goals.*;
import me.thegabro.playtimemanager.GUIs.JoinStreak.*;
import me.thegabro.playtimemanager.GUIs.Misc.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Updates.UpdateManager;
import me.thegabro.playtimemanager.Commands.*;
import me.thegabro.playtimemanager.Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.Events.JoinEventManager;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Events.QuitEventManager;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlayTimePlaceHolders;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.luckperms.api.LuckPerms;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;

import java.util.Objects;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private Configuration config;
    private DatabaseHandler databaseHandler;
    private boolean permissionsManagerConfigured;
    private boolean afkDetectionConfigured;
    private boolean placeholdersapiConfigured;
    private OnlineUsersManager onlineUsersManager;
    private DBUsersManager dbUsersManager;
    private JoinStreaksManager joinStreaksManager;
    private SessionManager sessionManager;
    private String configuredPlugin;
    public final String CURRENT_CONFIG_VERSION = "4.2";
    public final String SERVER_VERSION = Bukkit.getBukkitVersion().split("-")[0];
    public final boolean CACHE_DEBUG = false;
    @Override
    public void onEnable() {

        int BSTATS_PLUGIN_ID = 24739;
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        instance = this;

        LogFilter.registerFilter();

        config = Configuration.getInstance(this, this.getDataFolder(), "config", true, true);

        UpdateManager updateManager = UpdateManager.getInstance();
        updateManager.initialize();

        // Check config version and perform updates if needed
        if (!config.getString("config-version", null).equals(CURRENT_CONFIG_VERSION)) {

            try {
                this.databaseHandler = DatabaseHandler.getInstance();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize database for version update: " + e.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            boolean success = updateManager.performVersionUpdate(config.getString("config-version", null), CURRENT_CONFIG_VERSION);

            if(!success){
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        DatabaseMigration migration = new DatabaseMigration(this);
        if (!migration.checkAndExecuteMigration())
            getLogger().warning("Migration failed but continuing with source database");

        try {
            DatabaseHandler.resetInstance();
            this.databaseHandler = DatabaseHandler.getInstance();
            getLogger().info("Database initialized successfully");
        } catch (Exception e) {
            getLogger().severe("CRITICAL: Failed to initialize database connection!");
            getLogger().severe("Error: " + e.getMessage());
            getLogger().severe("Plugin will now disable. Please check your database configuration.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize singleton configurations

        PlaytimeFormatsConfiguration playtimeFormatsConfiguration = PlaytimeFormatsConfiguration.getInstance();
        playtimeFormatsConfiguration.initialize(this);

        GUIsConfiguration guiConfig = GUIsConfiguration.getInstance();
        guiConfig.initialize(this);

        CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
        commandsConfig.initialize(this);

        permissionsManagerConfigured = checkPermissionsPlugin();
        afkDetectionConfigured = checkAFKPlugin();

        GoalsManager goalsManager = GoalsManager.getInstance();
        goalsManager.initialize(this);

        onlineUsersManager = OnlineUsersManager.getInstance();
        dbUsersManager = DBUsersManager.getInstance();

        joinStreaksManager = JoinStreaksManager.getInstance();
        joinStreaksManager.initialize();
        joinStreaksManager.onServerReload();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
            placeholdersapiConfigured = true;
        }else{
            placeholdersapiConfigured = false;
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getServer().getPluginManager().registerEvents(ChatEventManager.getInstance(), this);

        Bukkit.getPluginManager().registerEvents(new AllGoalsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalSettingsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalRewardsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalRequirementsGui(), this);
        Bukkit.getPluginManager().registerEvents(new ConfirmationGui(), this);
        Bukkit.getPluginManager().registerEvents(new JoinStreakRewardSettingsGui(), this);
        Bukkit.getPluginManager().registerEvents(new AllJoinStreakRewardsGui(), this);
        Bukkit.getPluginManager().registerEvents(new JoinStreakRewardPrizesGui(), this);

        Objects.requireNonNull(getCommand("playtimegoal")).setExecutor(new PlaytimeGoal());

        Objects.requireNonNull(getCommand("playtime")).setExecutor(new PlayTimeCommandManager() {
        });
        Objects.requireNonNull(getCommand("playtime")).setTabCompleter(new PlayTimeCommandManager());

        Objects.requireNonNull(getCommand("playtimeaverage")).setExecutor(new PlaytimeAverage() {
        });
        Objects.requireNonNull(getCommand("playtimepercentage")).setExecutor(new PlaytimePercentage() {
        });
        Objects.requireNonNull(getCommand("playtimetop")).setExecutor(new PlaytimeTop() {
        });
        Objects.requireNonNull(getCommand("playtimereload")).setExecutor(new PlaytimeReload() {
        });
        Objects.requireNonNull(getCommand("playtimebackup")).setExecutor(new PlayTimeBackup() {
        });
        Objects.requireNonNull(getCommand("playtimejoinstreak")).setExecutor(new PlayTimeJoinStreak() {
        });
        Objects.requireNonNull(getCommand("claimrewards")).setExecutor(new ClaimRewards() {
        });
        Objects.requireNonNull(getCommand("playtimeattribute")).setExecutor(new PlayTimeAttributeCommand() {
        });
        Objects.requireNonNull(getCommand("playtimestats")).setExecutor(new PlayTimeStats() {
        });
        Objects.requireNonNull(getCommand("playtimemigration")).setExecutor(new PlayTimeMigration() {
        });
        onlineUsersManager.initialize();
        dbUsersManager.updateTopPlayersFromDB();

        sessionManager = new SessionManager();

        metrics.addCustomChart(new SimplePie("database_type_pie_chart", () -> {
            String dbType = config.getString("database-type", "sqlite").toLowerCase();

            return switch (dbType) {
                case "mysql", "mariadb", "postgresql", "sqlite" -> dbType;
                default -> "sqlite";
            };
        }));

        getLogger().info("has been enabled!");
    }

    @Override
    public void onDisable() {

        getLogger().info("Saving player data...");

        Bukkit.getScheduler().cancelTasks(this);

        if (onlineUsersManager != null) {
            // Persist each online user
            for (Player player : Bukkit.getOnlinePlayers()) {
                OnlineUser user = onlineUsersManager.getOnlineUser(player.getName());
                if (user != null) {
                    user.updateAllOnQuitSync(
                            user.getPlayerInstance().getStatistic(Statistic.PLAY_ONE_MINUTE)
                    );
                    onlineUsersManager.removeOnlineUser(user);
                }
            }

            onlineUsersManager.stopSchedules();
        }


        if (joinStreaksManager != null) {
            joinStreaksManager.cleanUp();
        }

        if (dbUsersManager != null) {
            dbUsersManager.clearCaches();
        }

        HandlerList.unregisterAll(this);

        if (databaseHandler != null) {
            databaseHandler.close();
        }
        DatabaseHandler.resetInstance();

        getLogger().info("has been disabled!");
    }

    public static PlayTimeManager getInstance() {
        return instance;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    public LuckPerms getLuckPermsApi() {
        return LuckPermsManager.getInstance(this).getLuckPermsApi();
    }

    public boolean isPermissionsManagerConfigured(){ return permissionsManagerConfigured; }

    public boolean isAfkDetectionConfigured(){ return afkDetectionConfigured; }

    public boolean isPlaceholdersAPIConfigured(){ return placeholdersapiConfigured; }

    public SessionManager getSessionManager() { return sessionManager; }

    private boolean checkAFKPlugin(){
        String configuredPlugin = config.getString("afk-detection-plugin", "none").toLowerCase();
        if ("essentials".equals(configuredPlugin)) {
            Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
            if(essentials != null && essentials.isEnabled()){
                try{
                    EssentialsAFKHook afkHook = EssentialsAFKHook.getInstance();
                    getServer().getPluginManager().registerEvents(afkHook, this);
                    getLogger().info("Essentials detected! Launching related functions");
                    return true;
                } catch (Exception e) {
                    getLogger().severe("ERROR: Failed to initialize Essentials API: " + e.getMessage());
                    return false;
                }
            }else{
                getLogger().warning(
                        "Failed to initialize afk detection: Essentials plugin configured but not found! " +
                                "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                );
                return false;
            }
        }
        else if ("purpur".equals(configuredPlugin)) {
            try {
                Class.forName("org.purpurmc.purpur.event.PlayerAFKEvent");
                PurpurAFKHook afkHook = PurpurAFKHook.getInstance();
                getServer().getPluginManager().registerEvents(afkHook, this);
                getLogger().info("Purpur AFK detection enabled! Launching related functions");
                return true;
            } catch (ClassNotFoundException e) {
                getLogger().warning(
                        "Failed to initialize afk detection: Purpur configured but server is not running Purpur! " +
                                "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                );
                return false;
            } catch (Exception e) {
                getLogger().severe("ERROR: Failed to initialize Purpur AFK detection: " + e.getMessage());
                return false;
            }
        }
        else if ("antiafkplus".equals(configuredPlugin)) {
            Plugin antiAFKPlus = Bukkit.getPluginManager().getPlugin("AntiAFKPlus");
            if(antiAFKPlus != null && antiAFKPlus.isEnabled()){
                try{
                    AntiAFKPlusAFKHook afkHook = AntiAFKPlusAFKHook.getInstance();
                    afkHook.register();
                    getLogger().info("AntiAFKPlus detected! Launching related functions");
                    return true;
                } catch (Exception e) {
                    getLogger().severe("ERROR: Failed to initialize AntiAFKPlus API: " + e.getMessage());
                    return false;
                }
            }else{
                getLogger().warning(
                        "Failed to initialize afk detection: AntiAFKPlus plugin configured but not found! " +
                                "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                );
                return false;
            }
        }
        else if ("jetsantiafkpro".equals(configuredPlugin)) {
            Plugin jetsAntiAFKPro = Bukkit.getPluginManager().getPlugin("JetsAntiAFKPro");
            if(jetsAntiAFKPro != null && jetsAntiAFKPro.isEnabled()){
                try{
                    Class<?> hookClass = Class.forName("me.thegabro.playtimemanager.ExternalPluginSupport.JetsAntiAFKPro.JetsAntiAFKProHook");
                    Object afkHook = hookClass.getMethod("getInstance").invoke(null);
                    hookClass.getMethod("init").invoke(afkHook);
                    getLogger().info("JetsAntiAFKPro detected! Launching related functions");
                    return true;
                } catch (Exception e) {
                    getLogger().severe("ERROR: Failed to initialize JetsAntiAFKPro API: " + e.getMessage());
                    return false;
                }
            }else{
                getLogger().warning(
                        "Failed to initialize afk detection: JetsAntiAFKPro plugin configured but not found! " +
                                "\nUntil this is resolved, PlayTimeManager will not be able to detect afk playtime"
                );
                return false;
            }
        }
        return false;
    }

    private boolean checkPermissionsPlugin() {
        configuredPlugin = config.getString("permissions-manager-plugin", "luckperms").toLowerCase();

        if ("luckperms".equals(configuredPlugin)) {
            Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPerms != null && luckPerms.isEnabled()) {
                try {
                    LuckPermsManager.getInstance(this);
                    getLogger().info("LuckPerms detected! Launching related functions");
                    return true;
                } catch (Exception e) {
                    getLogger().severe("ERROR: Failed to initialize LuckPerms API: " + e.getMessage());
                    return false;
                }
            } else {
                getLogger().warning(
                        "Failed to initialize permissions system: LuckPerms plugin configured but not found! " +
                                "\nUntil this is resolved, PlayTimeManager will not be able to manage permissions or groups"
                );
                return false;
            }
        }
        return false;
    }

    public String getAFKPlugin(){
        return configuredPlugin;
    }
}