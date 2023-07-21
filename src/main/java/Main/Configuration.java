package Main;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class Configuration {

    protected final boolean createIfNotExist, resource;
    protected final JavaPlugin plugin;

    protected FileConfiguration config;
    protected File file, path;
    protected String name;

    public Configuration(JavaPlugin instance, File path, String name, boolean createIfNotExist, boolean resource) {
        this.plugin = instance;
        this.path = path;
        this.name = name + ".yml";
        this.createIfNotExist = createIfNotExist;
        this.resource = resource;
        create();
        reloadConfig();
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
        save();
    }

    public void removeGroup(String groupname) {
        config.set("Groups." + groupname, null);
        save();
    }

    public long getGroupPlayTime(String groupname){
        return config.getLong("Groups." + groupname + ".time-required");
    }

    public HashMap<String, Long> getGroups() {
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
                return groups;
            }else{
                return null;
            }
        }else{
            return null;
        }
    }
}