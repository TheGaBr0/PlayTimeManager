package me.thegabro.playtimemanager;

import Commands.*;
import Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import Events.JoinEventManager;
import SQLiteDB.Database;
import SQLiteDB.LogFilter;
import SQLiteDB.SQLite;
import Events.QuitEventManager;
import PlaceHolders.PlayTimePlaceHolders;
import Users.OnlineUsersManager;
import Users.OnlineUsersManagerLuckPerms;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
@SuppressWarnings("ResultOfMethodCallIgnored")
public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private OnlineUsersManager onlineUsersManager;
    public LuckPerms luckPermsApi = null;
    private Configuration config;
    private Database db;
    String configVersion = "3.0";
    @Override
    public void onEnable() {

        instance = this;

        config = new Configuration(this.getDataFolder(), "config", true, true);

        if(!config.getVersion().equals(configVersion)){
            getLogger().info("Old config version detected, updating it to the latest one...");
            updateConfigFile();
            getLogger().info("Update completed! Latest version: "+configVersion);

        }

        LogFilter.registerFilter();
        this.db = new SQLite(this);
        this.db.load();

        if(Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Objects.requireNonNull(getCommand("playtimegroup")).setExecutor(new PlaytimeLuckPermsGroup());
            luckPermsApi = LuckPermsProvider.get();
            getLogger().info("LuckPerms detected! Launching related auto-promotion functions");
            onlineUsersManager = new OnlineUsersManagerLuckPerms();
        }else{
            onlineUsersManager = new OnlineUsersManager();
        }

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        Objects.requireNonNull(getCommand("playtime")).setExecutor(new PlayTimeCommandManager() {});
        Objects.requireNonNull(getCommand("playtimeaverage")).setExecutor(new PlaytimeAverage() {});
        Objects.requireNonNull(getCommand("playtimepercentage")).setExecutor(new PlaytimePercentage() {});
        Objects.requireNonNull(getCommand("playtimetop")).setExecutor(new PlaytimeTop() {});
        Objects.requireNonNull(getCommand("playtimereload")).setExecutor(new PlaytimeReload() {});
        //getCommand("playtimehelp").setExecutor(new PlaytimeHelp(this));

        getLogger().info("has been enabled!");

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

    public Database getDatabase() { return this.db; }

    private void updateConfigFile(){
        String playtimeSelfMessage = config.getPlaytimeSelfMessage();
        String playtimeOthersMessage = config.getPlaytimeOthersMessage();
        String luckpermsGoalSound = config.getLuckPermsGoalSound();
        String luckpermsGoalMessage = config.getLuckPermsGoalMessage();
        long luckPermsCheckRate = config.getLuckPermsCheckRate();
        boolean luckPermsCheckVerbose = config.getLuckPermsCheckVerbose();
        HashMap<String, Long> groups = config.getGroups();

        File configFile = new File(this.getDataFolder(), "config.yml");
        configFile.delete();

        config = new Configuration(this.getDataFolder(), "config", true, true);

        config.setLuckPermsCheckRate(luckPermsCheckRate);
        config.setLuckPermsCheckVerbose(luckPermsCheckVerbose);
        config.setLuckPermsGoalMessage(luckpermsGoalMessage);
        config.setLuckPermsGoalSound(luckpermsGoalSound);
        config.setPlaytimeOthersMessage(playtimeOthersMessage);
        config.setPlaytimeSelfMessage(playtimeSelfMessage);

        for (Map.Entry<String, Long> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            Long timeRequired = entry.getValue();
            config.setGroup(groupName, timeRequired);
        }

        config.reload();
    }

}
