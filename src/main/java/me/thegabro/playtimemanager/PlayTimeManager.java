package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.GUIs.Goals.AllGoalsGui;
import me.thegabro.playtimemanager.GUIs.Goals.GoalCommandsGui;
import me.thegabro.playtimemanager.GUIs.Goals.GoalPermissionsGui;
import me.thegabro.playtimemanager.GUIs.Goals.GoalSettingsGui;
import me.thegabro.playtimemanager.GUIs.JoinStreak.*;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.Updates.UpdateManager;
import me.thegabro.playtimemanager.Commands.*;
import me.thegabro.playtimemanager.Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.Events.JoinEventManager;
import me.thegabro.playtimemanager.GUIs.*;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.SQLiteDB.LogFilter;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import me.thegabro.playtimemanager.Events.QuitEventManager;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlayTimePlaceHolders;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.luckperms.api.LuckPerms;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;

import java.io.File;
import java.util.Objects;
@SuppressWarnings("ResultOfMethodCallIgnored")
public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private Configuration config;
    private PlayTimeDatabase db;
    private boolean permissionsManagerConfigured;
    private final String CURRENTCONFIGVERSION = "3.5";
    private final String CURRENTGOALSCONFIGVERSION = "1.0";
    private final String CURRENTREWARDSCONFIGVERSION = "1.0";
    private OnlineUsersManager onlineUsersManager;
    private DBUsersManager dbUsersManager;
    private final String serverVersion = Bukkit.getBukkitVersion().split("-")[0];
    private SessionManager sessionManager;

    @Override
    public void onEnable() {

        int BSTATS_PLUGIN_ID = 24739;
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);



        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        instance = this;

        LogFilter.registerFilter();

        this.db = new SQLite(this);
        this.db.load();

        UpdateManager updateManager = UpdateManager.getInstance(this);
        updateManager.initialize();

        // Check config version and perform updates if needed
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            if (!config.getString("config-version").equals(CURRENTCONFIGVERSION)) {
                updateManager.performVersionUpdate(config.getString("config-version"), CURRENTCONFIGVERSION);
            }
        }

        config = new Configuration(this.getDataFolder(), "config", true, true);

        GoalsManager goalsManager = GoalsManager.getInstance();
        goalsManager.initialize(this);

        onlineUsersManager = OnlineUsersManager.getInstance();
        dbUsersManager = DBUsersManager.getInstance();

        JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();
        joinStreaksManager.initialize(this);

        permissionsManagerConfigured = checkPermissionsPlugin();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getServer().getPluginManager().registerEvents(ChatEventManager.getInstance(), this);

        Bukkit.getPluginManager().registerEvents(new AllGoalsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalSettingsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalPermissionsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalCommandsGui(), this);
        Bukkit.getPluginManager().registerEvents(new ConfirmationGui(), this);
        Bukkit.getPluginManager().registerEvents(new JoinStreakRewardSettingsGui(), this);
        Bukkit.getPluginManager().registerEvents(new AllJoinStreakRewardsGui(), this);
        Bukkit.getPluginManager().registerEvents(new JoinStreakPermissionsGui(), this);
        Bukkit.getPluginManager().registerEvents(new JoinStreakCommandsGui(), this);

        Objects.requireNonNull(getCommand("playtimegoal")).setExecutor(new PlaytimeGoal());
        Objects.requireNonNull(getCommand("playtime")).setExecutor(new PlayTimeCommandManager() {
        });
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

        onlineUsersManager.initialize();
        dbUsersManager.updateTopPlayersFromDB();

        sessionManager = new SessionManager();

        getLogger().info("has been enabled!");


    }

    @Override
    public void onDisable() {
        onlineUsersManager.stopSchedules();
        for(Player p : Bukkit.getOnlinePlayers()){
            onlineUsersManager.removeOnlineUser(onlineUsersManager.getOnlineUser(Objects.requireNonNull(p.getPlayer()).getName()));
        }
        db.close();
        HandlerList.unregisterAll(this);
        dbUsersManager.clearCache();
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

    public PlayTimeDatabase getDatabase() { return this.db; }

    public LuckPerms getLuckPermsApi() {
        return LuckPermsManager.getInstance(this).getLuckPermsApi();
    }

    public boolean isPermissionsManagerConfigured(){ return permissionsManagerConfigured; }

    public SessionManager getSessionManager() { return sessionManager; }

    private boolean checkPermissionsPlugin() {
        String configuredPlugin = config.getPermissionsManagerPlugin().toLowerCase();

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

}
