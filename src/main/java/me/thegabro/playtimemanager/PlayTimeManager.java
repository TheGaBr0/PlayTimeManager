package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.updaters.Version304To31Updater;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager;

import java.util.Objects;
@SuppressWarnings("ResultOfMethodCallIgnored")
public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private Configuration config;
    private PlayTimeDatabase db;
    private boolean permissionsManagerConfigured;
    private final String CURRENTCONFIGVERSION = "3.2";
    private final String CURRENTGOALSCONFIGVERSION = "1.0";
    private OnlineUsersManager onlineUsersManager;
    private DBUsersManager dbUsersManager;

    @Override
    public void onEnable() {

        instance = this;

        config = new Configuration(this.getDataFolder(), "config", true, true);

        LogFilter.registerFilter();


        this.db = new SQLite(this);
        this.db.load();

        GoalsManager.initialize(this);
        onlineUsersManager = OnlineUsersManager.getInstance();
        dbUsersManager = DBUsersManager.getInstance();

        permissionsManagerConfigured = checkPermissionsPlugin();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getServer().getPluginManager().registerEvents(new ChatEventManager(), this);

        Bukkit.getPluginManager().registerEvents(new AllGoalsGui(), this);
        Bukkit.getPluginManager().registerEvents(new GoalSettingsGui(), this);
        Bukkit.getPluginManager().registerEvents(new PermissionsGui(), this);
        Bukkit.getPluginManager().registerEvents(new CommandsGui(), this);
        Bukkit.getPluginManager().registerEvents(new ConfirmationGui(), this);

        Objects.requireNonNull(getCommand("playtimegoal")).setExecutor(new PlaytimeGoal());
        Objects.requireNonNull(getCommand("playtime")).setExecutor(new PlayTimeCommandManager() {});
        Objects.requireNonNull(getCommand("playtimeaverage")).setExecutor(new PlaytimeAverage() {});
        Objects.requireNonNull(getCommand("playtimepercentage")).setExecutor(new PlaytimePercentage() {});
        Objects.requireNonNull(getCommand("playtimetop")).setExecutor(new PlaytimeTop() {});
        Objects.requireNonNull(getCommand("playtimereload")).setExecutor(new PlaytimeReload() {});
        //getCommand("playtimehelp").setExecutor(new PlaytimeHelp(this));

        if(!config.getVersion().equals(CURRENTCONFIGVERSION)){
            if(config.getVersion().equals("3.1")){
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.1 config version detected, updating it to the latest one...");
                Version304To31Updater updater = new Version304To31Updater(this);
                updater.performFullUpgrade();
                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r"+ CURRENTCONFIGVERSION);
            }else{
                this.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
            }

        }

        if(!config.getGoalsVersion().equals(CURRENTGOALSCONFIGVERSION)){
            this.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown goals config version detected! Something may break!");

        }
        dbUsersManager.updateTopPlayersFromDB();
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

    public OnlineUsersManager getOnlineUsersManager() {
        return onlineUsersManager;
    }

    public DBUsersManager getDbUsersManager() {
        return dbUsersManager;
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
