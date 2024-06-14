package me.thegabro.playtimemanager;

import UsersDatabases.OnlineUsersManagerLuckPerms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Set;

public class Configuration {

    private final boolean createIfNotExist, resource;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    private FileConfiguration config;
    private File file;
    private final File path;
    private final String name;
    private HashMap<String, Long> groups;
    private long luckpermsCheckRate;
    private boolean luckpermsCheckVerbose;

    public Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reloadConfig();
        updateLuckPermsCheck();
        updateLuckPermsGroups();
    }

    //Getters, variables and constructors

    private void save() {
        try {
            config.save(file);
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }

    private File reloadFile() {
        file = new File(path, name);
        return file;
    }

    private FileConfiguration reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);
        return config;
    }

    public void reload() {
        reloadFile();
        reloadConfig();
        updateLuckPermsCheck();
        updateLuckPermsGroups();
    }

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
                exc.printStackTrace();
            }
        }
    }

    public void addGroup(String groupname, long timeRequired) {
        config.createSection("Groups." + groupname + ".time-required");
        config.set("Groups." + groupname + ".time-required", timeRequired);
        updateLuckPermsGroups();
        save();
    }

    public void removeGroup(String groupname) {
        config.set("Groups." + groupname, null);
        updateLuckPermsGroups();
        save();
    }

    public HashMap<String, Long> getGroups(){
        return groups;
    }


    public long getGroupPlayTime(String groupname){
        return groups.get(groupname);
    }

    private void updateLuckPermsGroups() {
        HashMap<String, Long> groups = new HashMap<String, Long>();
        if (config.contains("Groups")) {
            ConfigurationSection groupsSection = config.getConfigurationSection("Groups");
            if (groupsSection != null) {
                Set<String> groupKeys = groupsSection.getKeys(false);
                for (String groupName : groupKeys) {
                    ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupName);

                    if (groupSection != null) {
                        long timeRequired = config.getInt("Groups."+groupName+".time-required");

                        groups.put(groupName, timeRequired);

                    }
                }
                this.groups = groups;
            }else{
                this.groups = new HashMap<String, Long>();
            }
        }else{
            this.groups = new HashMap<String, Long>();
        }
    }

    private void updateLuckPermsCheck(){
        this.luckpermsCheckRate = config.getLong("luckperms-check-rate");
        this.luckpermsCheckVerbose = config.getBoolean("luckperms-check-verbose");
    }

    public long getLuckPermsCheckRate(){
        return luckpermsCheckRate;
    }

    public boolean getLuckPermsCheckVerbose(){
        return luckpermsCheckVerbose;
    }
}