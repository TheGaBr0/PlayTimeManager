package me.thegabro.playtimemanager;

import Commands.*;
import Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import Events.JoinEventManager;
import SQLiteDB.Database;
import SQLiteDB.SQLite;
import Events.QuitEventManager;
import PlaceHolders.PlayTimePlaceHolders;
import UsersDatabases.OnlineUsersManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayTimeManager extends JavaPlugin{

    private static PlayTimeManager instance;
    private OnlineUsersManager onlineUsersManager;
    public LuckPerms luckPermsApi = null;
    private Configuration config;
    private Database db;

    @Override
    public void onEnable() {


        if (!getDataFolder().exists()) getDataFolder().mkdir();
        saveDefaultConfig();

        instance = this;
        this.db = new SQLite(this);
        this.db.load();
        config = new Configuration(this, this.getDataFolder(), "config", true, true);

        if(Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            getCommand("playtimegroup").setExecutor(new PlaytimeLuckPermsGroup());
            luckPermsApi = LuckPermsProvider.get();
            //usersManager = new UsersManagerLuckPerms();
            onlineUsersManager = new OnlineUsersManager();
        }else{
            onlineUsersManager = new OnlineUsersManager();
        }


        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayTimePlaceHolders().register();
        }

        getServer().getPluginManager().registerEvents(new QuitEventManager(), this);
        getServer().getPluginManager().registerEvents(new JoinEventManager(), this);
        getCommand("playtime").setExecutor(new PlayTimeCommandManager() {});
        getCommand("playtimeaverage").setExecutor(new PlaytimeAverage() {});
        getCommand("playtimepercentage").setExecutor(new PlaytimePercentage() {});
        getCommand("playtimetop").setExecutor(new PlaytimeTop() {});
        //getCommand("playtimehelp").setExecutor(new PlaytimeHelp(this));

        getLogger().info("has been enabled!");

    }

    @Override
    public void onDisable() {
        for(Player p : Bukkit.getOnlinePlayers()){
            onlineUsersManager.removeOnlineUser(onlineUsersManager.getOnlineUser(p.getPlayer().getName()));
        }

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



}
