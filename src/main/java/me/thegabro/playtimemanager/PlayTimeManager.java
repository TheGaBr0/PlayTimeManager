package me.thegabro.playtimemanager;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import com.jeff_media.updatechecker.UserAgentBuilder;
import me.thegabro.playtimemanager.Updates.Version304To31Updater;
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
import me.thegabro.playtimemanager.Updates.Version31to311Updater;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
    private final String CURRENTCONFIGVERSION = "3.3";
    private final String CURRENTGOALSCONFIGVERSION = "1.0";
    private OnlineUsersManager onlineUsersManager;
    private DBUsersManager dbUsersManager;
    private GoalsManager goalsManager;
    private static final String SPIGOT_RESOURCE_ID = "118284";


    @Override
    public void onEnable() {

        new UpdateChecker(this, UpdateCheckSource.SPIGET, SPIGOT_RESOURCE_ID)
                .setDownloadLink("https://www.spigotmc.org/resources/playtimemanager.118284/")
                .setChangelogLink("https://www.spigotmc.org/resources/playtimemanager.118284/updates")
                .setUserAgent(new UserAgentBuilder().addPluginNameAndVersion())
                .checkEveryXHours(24)
                .checkNow();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        instance = this;

        LogFilter.registerFilter();

        this.db = new SQLite(this);
        this.db.load();

        File configFileTest = new File(getDataFolder(), "config.yml");
        if (configFileTest.exists()) {
            FileConfiguration configTest = YamlConfiguration.loadConfiguration(configFileTest);
            if (!configTest.getString("config-version").equals(CURRENTCONFIGVERSION)) {
                if (configTest.getString("config-version").equals("3.1")) {
                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.1 config version detected, updating it to the latest one...");
                    Version304To31Updater updater = new Version304To31Updater(this);
                    updater.performUpgrade();

                    Version31to311Updater updater2 = new Version31to311Updater(this);
                    updater2.performUpgrade();

                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + CURRENTCONFIGVERSION);
                } else if (configTest.getString("config-version").equals("3.2")) {
                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 3.2 config version detected, updating it to the latest one...");
                    Version31to311Updater updater2 = new Version31to311Updater(this);
                    updater2.performUpgrade();
                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Update completed! Latest version: §r" + CURRENTCONFIGVERSION);
                } else {
                    this.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown config version detected! Something may break!");
                }

            }
            configFileTest = new File(getDataFolder(), "config.yml");
            configTest = YamlConfiguration.loadConfiguration(configFileTest);
            if (!configTest.getString("goals-config-version").equals(CURRENTGOALSCONFIGVERSION)) {
                this.getLogger().severe("[§6PlayTime§eManager§f]§7 Unknown goals config version detected! Something may break!");

            }
        }

        config = new Configuration(this.getDataFolder(), "config", true, true);

        goalsManager = GoalsManager.getInstance();
        goalsManager.initialize(this);
        onlineUsersManager = OnlineUsersManager.getInstance();
        dbUsersManager = DBUsersManager.getInstance();

        permissionsManagerConfigured = checkPermissionsPlugin();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
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

        onlineUsersManager.initialize();
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
