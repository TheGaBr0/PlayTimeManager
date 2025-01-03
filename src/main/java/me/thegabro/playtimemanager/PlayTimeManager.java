package me.thegabro.playtimemanager;

import Commands.*;
import Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import Events.JoinEventManager;
import GUIs.AllGoalsGui;
import GUIs.ConfirmationGui;
import GUIs.GoalSettingsGui;
import GUIs.PermissionsGui;
import Goals.Goal;
import Goals.GoalManager;
import SQLiteDB.PlayTimeDatabase;
import SQLiteDB.LogFilter;
import SQLiteDB.SQLite;
import Events.QuitEventManager;
import PlaceHolders.PlayTimePlaceHolders;
import Users.OnlineUsersManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;
@SuppressWarnings("ResultOfMethodCallIgnored")
public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private OnlineUsersManager onlineUsersManager;
    private Configuration config;
    private PlayTimeDatabase db;
    private boolean permissionsManagerConfigured;
    private final String CONFIGVERSION = "3.2";
    private final String GOALSCONFIGVERSION = "1.0";

    @Override
    public void onEnable() {

        instance = this;

        config = new Configuration(this.getDataFolder(), "config", true, true);

        LogFilter.registerFilter();
        this.db = new SQLite(this);
        this.db.load();


        if(!config.getVersion().equals(CONFIGVERSION)){
            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Old config version detected, updating it to the latest one...");
            updateConfigFile();

            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r"+ CONFIGVERSION);

        }

        if(!config.getGoalsVersion().equals(GOALSCONFIGVERSION)){
            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Old goals config version detected, updating it to the latest one...");
            updateGoalsConfigFile();

            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r"+ GOALSCONFIGVERSION);

        }


        onlineUsersManager = new OnlineUsersManager();

        permissionsManagerConfigured = checkPermissionsPlugin();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getServer().getPluginManager().registerEvents(new AllGoalsGui(), this);
        getServer().getPluginManager().registerEvents(new GoalSettingsGui(), this);
        getServer().getPluginManager().registerEvents(new PermissionsGui(), this);
        getServer().getPluginManager().registerEvents(new ConfirmationGui(), this);

        Objects.requireNonNull(getCommand("playtimegoal")).setExecutor(new PlaytimeGoal());
        Objects.requireNonNull(getCommand("playtime")).setExecutor(new PlayTimeCommandManager() {});
        Objects.requireNonNull(getCommand("playtimeaverage")).setExecutor(new PlaytimeAverage() {});
        Objects.requireNonNull(getCommand("playtimepercentage")).setExecutor(new PlaytimePercentage() {});
        Objects.requireNonNull(getCommand("playtimetop")).setExecutor(new PlaytimeTop() {});
        Objects.requireNonNull(getCommand("playtimereload")).setExecutor(new PlaytimeReload() {});
        //getCommand("playtimehelp").setExecutor(new PlaytimeHelp(this));

        GoalManager.initialize(this);

        getLogger().info("has been enabled!");

        getUsersManager().restartSchedule();

    }

    @Override
    public void onDisable() {
        for(Player p : Bukkit.getOnlinePlayers()){
            onlineUsersManager.removeOnlineUser(onlineUsersManager.getOnlineUser(Objects.requireNonNull(p.getPlayer()).getName()));
        }
        db.close();
        getLogger().info("has been disabled!");
    }


    public static PlayTimeManager getInstance() {
        return instance;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public OnlineUsersManager getUsersManager(){return onlineUsersManager;}

    public PlayTimeDatabase getDatabase() { return this.db; }

    public boolean isPermissionsManagerConfigured(){ return permissionsManagerConfigured; }

    private boolean checkPermissionsPlugin() {
        String configuredPlugin = config.getPermissionsManagerPlugin().toLowerCase();

        switch (configuredPlugin) {
            case "luckperms":
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 LuckPerms detected! Launching related functions");
                    return true;
                } else {
                    Bukkit.getServer().getConsoleSender().sendMessage("§4[§cPlayTime§4Manager§4] §4ERROR: §cLuckPerms is configured but not installed! Goal check will be started.");
                    return false;
                }
            default:
                Bukkit.getServer().getConsoleSender().sendMessage("§4[§cPlayTime§4Manager§4] §4ERROR: §cInvalid permissions plugin configured: " + configuredPlugin + ". Goal check will be started.");
                return false;
        }
    }

    private void updateConfigFile(){
        String playtimeSelfMessage = config.getPlaytimeSelfMessage();
        String playtimeOthersMessage = config.getPlaytimeOthersMessage();

        //planned for removal, upgrade from 3.0.4 to 3.1 due to groups being transformed into goals
        //---------------------------------
        long goalsCheckRate = config.getLuckPermsCheckRate();
        boolean goalsCheckVerbose = config.getLuckPermsCheckVerbose();

        Map<String, Long> dbgroups = db.getAllGroupsData();

        for (Map.Entry<String, Long> entry : dbgroups.entrySet()) {
            String name = entry.getKey();   // goal name (String)
            Long time = entry.getValue(); // goal time (Long)
            Goal g = new Goal(this, name, time, true);
            g.addPermission("group."+name);
        }

        db.dropGroupsTable();

        //---------------------------------

        File configFile = new File(this.getDataFolder(), "config.yml");
        configFile.delete();

        config = new Configuration(this.getDataFolder(), "config", true, true);


        config.setPlaytimeOthersMessage(playtimeOthersMessage);
        config.setPlaytimeSelfMessage(playtimeSelfMessage);
        //planned for removal, upgrade from 3.0.4 to 3.1 due to groups being transformed into goals
        //---------------------------------
        config.setGoalsCheckRate(goalsCheckRate);
        config.setGoalsCheckVerbose(goalsCheckVerbose);
        //---------------------------------
        config.reload();
    }

    private void updateGoalsConfigFile(){}
}
