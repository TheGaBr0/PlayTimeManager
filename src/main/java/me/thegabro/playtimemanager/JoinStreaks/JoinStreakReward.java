package me.thegabro.playtimemanager.JoinStreaks;

import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class JoinStreakReward {
    // Fields
    private final PlayTimeManager plugin;
    private final JoinStreaksManager rewardsManager = JoinStreaksManager.getInstance();
    private final int id;
    private int requiredJoins; // Number of joins required to receive the reward
    private final File rewardFile;
    private ArrayList<String> permissions = new ArrayList<>();
    private ArrayList<String> commands = new ArrayList<>();
    private String rewardMessage;
    private String rewardSound;

    // Constructor
    public JoinStreakReward(PlayTimeManager plugin, int id, int requiredJoins) {
        this.plugin = plugin;
        this.id = id;
        this.requiredJoins = requiredJoins;
        this.rewardFile = new File(plugin.getDataFolder() + File.separator + "Rewards" + File.separator + id + ".yml");
        loadFromFile();
        saveToFile();
        rewardsManager.addReward(this);
    }

    // Core file operations
    private void loadFromFile() {
        if (rewardFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(rewardFile);
            requiredJoins = config.getInt("required-joins", requiredJoins);
            rewardMessage = config.getString("reward-message", getDefaultRewardMessage());
            rewardSound = config.getString("reward-sound", getDefaultRewardSound());
            permissions = new ArrayList<>(config.getStringList("permissions"));
            commands = new ArrayList<>(config.getStringList("commands"));
        } else {
            rewardMessage = getDefaultRewardMessage();
            rewardSound = getDefaultRewardSound();
            permissions = new ArrayList<>();
            commands = new ArrayList<>();
        }
    }

    private void saveToFile() {
        try {
            if (!rewardFile.exists()) {
                rewardFile.getParentFile().mkdirs();
                rewardFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();
            config.options().setHeader(Arrays.asList(
                    "GUIDE OF AVAILABLE OPTIONS:",
                    "---------------------------",
                    "required-joins specifies the number of joins needed for a player to receive this reward.",
                    "---------------------------",
                    "reward-sound is played to a player if it reaches the join streak specified in this config.",
                    "A list of available sounds can be found here: https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                    "Replace '<VERSION>' in the link with your current minecraft version. If it doesn't work try with the ",
                    "latest update of your version (e.g. '1.19' doesn't work and you need to use '1.19.4')",
                    "---------------------------",
                    "reward-message is showed to a player if it reaches the join streak specified in this config.",
                    "Available placeholders: %REQUIRED_JOINS%, %PLAYER_NAME%",
                    "---------------------------",
                    "permissions defines what permissions will be granted to a player when they reach this reward",
                    "You can specify multiple permissions and groups that will all be granted. The plugin will assume that",
                    "the group has already been created using the permissions manager plugin specified in the main config.",
                    "---------------------------",
                    "commands defines a list of commands that will be executed when a player reaches this reward",
                    "Available placeholders: %PLAYER_NAME%",
                    "Example commands:",
                    "- '/give %PLAYER_NAME% diamond 64'",
                    "- '/broadcast %PLAYER_NAME% has reached an amazing join streak!'"
            ));
            config.set("required-joins", requiredJoins);
            config.set("reward-sound", rewardSound);
            config.set("reward-message", rewardMessage);
            config.set("permissions", permissions);
            config.set("commands", commands);
            config.save(rewardFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save reward file for " + id + ": " + e.getMessage());
        }
    }

    // Default values
    private String getDefaultRewardSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultRewardMessage() {
        return "&6[PlayTimeManager]&7 Great job, &e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 times " +
                "and unlocked a new reward! Use &e/claimreward&7 to collect it!";
    }

    // Basic getters
    public int getId() {
        return id;
    }

    public int getRequiredJoins() {
        return requiredJoins;
    }

    public String getRewardMessage() {
        return rewardMessage;
    }

    public String getRewardSound() {
        return rewardSound;
    }

    public ArrayList<String> getCommands() {
        return commands;
    }

    public ArrayList<String> getPermissions() {
        return permissions;
    }

    // Setters and modifiers
    public void setRequiredJoins(int requiredJoins) {

        if(requiredJoins <= 0)
            this.requiredJoins = 1;
        else
            this.requiredJoins = requiredJoins;

        saveToFile();
    }

    public void setRewardMessage(String rewardMessage) {
        this.rewardMessage = rewardMessage;
        saveToFile();
    }

    public void setRewardSound(String rewardSound) {
        this.rewardSound = rewardSound;
        saveToFile();
    }

    public void addCommand(String command) {
        commands.add(command);
        saveToFile();
    }

    public void removeCommand(String command) {
        commands.remove(command);
        saveToFile();
    }

    public void addPermission(String permission) {
        permissions.add(permission);
        saveToFile();
    }

    public void removePermission(String permission) {
        permissions.remove(permission);
        saveToFile();
    }

    public void kill() {
        rewardsManager.removeReward(this);
        deleteFile();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JoinStreakReward reward = (JoinStreakReward) o;
        return Objects.equals(id, reward.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public void deleteFile() {
        if (rewardFile.exists()) {
            if (!rewardFile.delete()) {
                plugin.getLogger().warning("Failed to delete reward file for " + id);
            }
        }
    }


    @Override
    public String toString() {
        return "JoinStreakReward{" +
                "id='" + id + '\'' +
                ", requiredJoins=" + requiredJoins +
                ", permissions=" + permissions.size() +
                ", commands=" + commands.size() +
                ", message='" + rewardMessage + '\'' +
                ", sound='" + rewardSound + '\'' +
                '}';
    }
}


