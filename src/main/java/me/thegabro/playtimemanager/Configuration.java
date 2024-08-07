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
    private String playtimeSelfMessage;
    private String playtimeOthersMessage;

    public Configuration(File path, String name, boolean createIfNotExist, boolean resource) {
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reload();
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
        updateMessages();
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

    //planned for removal, update groups from 3.0.3 as they are moved into the db
    //------------------------------------------
    public HashMap<String, Long> getGroups(){
        updateLuckPermsGroups();
        return groups;
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
    //------------------------------------------
    private void updateLuckPermsSettings(){
        this.luckpermsCheckRate = config.getLong("luckperms-check-rate");
        this.luckpermsCheckVerbose = config.getBoolean("luckperms-check-verbose");
        this.luckpermsGoalSound = config.getString("luckperms-time-goal-sound");
    }

    private void updateMessages(){
        this.luckpermsGoalMessage = config.getString("luckperms-time-goal-message");
        this.playtimeSelfMessage = config.getString("playtime-self-message");
        this.playtimeOthersMessage = config.getString("playtime-others-message");
    }

    public long getLuckPermsCheckRate(){
        return luckpermsCheckRate;
    }

    public void setLuckPermsCheckRate(Long rate){
        if (rate != null) {
            config.set("luckperms-check-rate", rate);
            save();
        }
    }

    public boolean getLuckPermsCheckVerbose(){
        return luckpermsCheckVerbose;
    }

    public void setLuckPermsCheckVerbose(Boolean verbose){
        if (verbose != null) {
            config.set("luckperms-check-verbose", verbose);
            save();
        }
    }

    public String getLuckPermsGoalMessage(){
        return luckpermsGoalMessage;
    }

    public void setLuckPermsGoalMessage(String message){
        if (message != null) {
            config.set("luckperms-time-goal-message", message);
            save();
        }
    }

    public String getLuckPermsGoalSound(){
        return luckpermsGoalSound;
    }

    public void setLuckPermsGoalSound(String sound){
        if (sound != null) {
            config.set("luckperms-time-goal-sound", sound);
            save();
        }
    }

    public String getPlaytimeSelfMessage(){
        return playtimeSelfMessage;
    }

    public void setPlaytimeSelfMessage(String message){
        if (message != null) {
            config.set("playtime-self-message", message);
            save();
        }
    }

    public String getPlaytimeOthersMessage(){
        return playtimeOthersMessage;
    }

    public void setPlaytimeOthersMessage(String message){
        if (message != null) {
            config.set("playtime-others-message", message);
            save();
        }
    }

    public String getVersion(){
        String version = config.getString("config-version");

        if(version == null)
            return "null";
        else
            return version;
    }
}