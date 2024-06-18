package me.thegabro.playtimemanager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Set;
@SuppressWarnings("ResultOfMethodCallIgnored")
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
    private String luckpermsGoalMessage;
    private String luckpermsGoalSound;

    public Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reloadConfig();
        updateLuckPermsSettings();
        updateLuckPermsGroups();
    }

    //Getters, variables and constructors

    private void save() {
        try {
            config.save(file);
        } catch (Exception exc) {
            plugin.getLogger().severe(String.valueOf(exc));
        }
    }

    private void reloadFile() {
        file = new File(path, name);
    }

    private void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        reloadFile();
        reloadConfig();
        updateLuckPermsSettings();
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
                plugin.getLogger().severe(String.valueOf(exc));
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
        HashMap<String, Long> groups = new HashMap<>();
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
                this.groups = new HashMap<>();
            }
        }else{
            this.groups = new HashMap<>();
        }
    }

    private void updateLuckPermsSettings(){
        this.luckpermsCheckRate = config.getLong("luckperms-check-rate");
        this.luckpermsCheckVerbose = config.getBoolean("luckperms-check-verbose");
        this.luckpermsGoalMessage = config.getString("luckperms-time-goal-message");
        this.luckpermsGoalSound = config.getString("luckperms-time-goal-sound");
    }

    public long getLuckPermsCheckRate(){
        return luckpermsCheckRate;
    }

    public boolean getLuckPermsCheckVerbose(){
        return luckpermsCheckVerbose;
    }

    public String getLuckPermsGoalMessage(){
        return luckpermsGoalMessage;
    }

    public String getLuckPermsGoalSound(){
        return luckpermsGoalSound;
    }


}