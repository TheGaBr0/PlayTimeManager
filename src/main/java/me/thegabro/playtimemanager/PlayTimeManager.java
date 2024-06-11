package me.thegabro.playtimemanager;

import Commands.*;
import Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import Events.JoinEventManager;
import SQLiteDB.Database;
import SQLiteDB.SQLite;
import UsersDatabases.*;
import Events.QuitEventManager;
import PlaceHolders.PlayTimePlaceHolders;
import UsersDatabases.UsersManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private DataCombiner dbDataCombiner;
    private UsersManager usersManager;
    public LuckPerms luckPermsApi = null;
    private Configuration config;
    private Database db;

    @Override
    public void onEnable() {

        instance = this;
        this.db = new SQLite(this);
        this.db.load();
        config = new Configuration(this, this.getDataFolder(), "config", true, true);

        if(Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            getCommand("playtimegroup").setExecutor(new PlaytimeLuckPermsGroup());
            luckPermsApi = LuckPermsProvider.get();
            //usersManager = new UsersManagerLuckPerms();
            usersManager = new UsersManager();
        }else{
            usersManager = new UsersManager();
        }

        dbDataCombiner = new DataCombiner();

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getCommand("playtime").setExecutor(new PlayTimeCommandManager() {});
        getCommand("playtimeaverage").setExecutor(new PlaytimeAverage() {});
        getCommand("playtimestats").setExecutor(new PlaytimeStats() {});
        getCommand("playtimetop").setExecutor(new PlaytimeTop() {});
        //getCommand("playtimehelp").setExecutor(new PlaytimeHelp(this));

        getLogger().info("has been enabled!");

    }

    @Override
    public void onDisable() {
        for(Player p : Bukkit.getOnlinePlayers()){
            usersManager.removeOnlineUser(usersManager.getUserByNickname(p.getPlayer().getName()));
        }

        getLogger().info("has been disabled!");
    }


    public static PlayTimeManager getInstance() {
        return instance;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public UsersManager getUsersManager(){return usersManager;}

    public DataCombiner getDbDataCombiner(){return dbDataCombiner;}

    public Database getDatabase() { return this.db; }



}
