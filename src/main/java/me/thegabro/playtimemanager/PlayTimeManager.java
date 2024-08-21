package me.thegabro.playtimemanager;

import Commands.*;
import Commands.PlayTimeCommandManager.PlayTimeCommandManager;
import Events.JoinEventManager;
import SQLiteDB.PlayTimeDatabase;
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
    private PlayTimeDatabase db;
    private boolean isLuckPermsLoaded;
    public Map<String, Long> groups;
    @Override
    public void onEnable() {

        instance = this;

        config = new Configuration(this.getDataFolder(), "config", true, true);

        LogFilter.registerFilter();
        this.db = new SQLite(this);
        this.db.load();

        String configVersion = "3.1";
        if(!config.getVersion().equals(configVersion)){
            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Old config version detected, updating it to the latest one...");
            updateConfigFile();

            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r"+ configVersion);

        }

        loadGroups();

        if(Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Objects.requireNonNull(getCommand("playtimegroup")).setExecutor(new PlaytimeLuckPermsGroup());
            luckPermsApi = LuckPermsProvider.get();
            Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 LuckPerms detected! Launching related auto-promotion functions");
            onlineUsersManager = new OnlineUsersManagerLuckPerms();
            isLuckPermsLoaded = true;
        }else{
            onlineUsersManager = new OnlineUsersManager();
            isLuckPermsLoaded = false;
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

    public void loadGroups(){
        groups = db.getAllGroupsData();
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

    private void updateConfigFile(){
        String playtimeSelfMessage = config.getPlaytimeSelfMessage();
        String playtimeOthersMessage = config.getPlaytimeOthersMessage();
        String luckpermsGoalSound = config.getLuckPermsGoalSound();
        String luckpermsGoalMessage = config.getLuckPermsGoalMessage();
        long luckPermsCheckRate = config.getLuckPermsCheckRate();
        boolean luckPermsCheckVerbose = config.getLuckPermsCheckVerbose();
        //planned for removal, update groups from 3.0.3 as they are moved into the db
        for (Map.Entry<String, Long> entry : config.getGroups().entrySet()) {

            String groupName = entry.getKey();
            Long timeRequired = entry.getValue();
            db.addGroup(groupName, timeRequired);
        }

        File configFile = new File(this.getDataFolder(), "config.yml");
        configFile.delete();

        config = new Configuration(this.getDataFolder(), "config", true, true);

        config.setLuckPermsCheckRate(luckPermsCheckRate);
        config.setLuckPermsCheckVerbose(luckPermsCheckVerbose);
        config.setLuckPermsGoalMessage(luckpermsGoalMessage);
        config.setLuckPermsGoalSound(luckpermsGoalSound);
        config.setPlaytimeOthersMessage(playtimeOthersMessage);
        config.setPlaytimeSelfMessage(playtimeSelfMessage);

        config.reload();
    }

    public boolean isLuckPermsLoaded() {
        return isLuckPermsLoaded;
    }
}
