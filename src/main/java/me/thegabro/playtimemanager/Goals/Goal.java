package me.thegabro.playtimemanager.Goals;

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
import java.util.List;
import java.util.Objects;

public class Goal {
    // Fields
    private final PlayTimeManager plugin;
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private String name;
    private final File goalFile;
    private GoalRewardRequirement requirements;
    private ArrayList<String> rewardPermissions = new ArrayList<>();
    private ArrayList<String> rewardCommands = new ArrayList<>();
    private String goalMessage;
    private String goalSound;
    private boolean active;

    public Goal(PlayTimeManager plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");
        this.requirements = new GoalRewardRequirement();
        loadFromFile(); // Load before modifying anything
        goalsManager.addGoal(this);
    }

    public Goal(PlayTimeManager plugin, String name, boolean active) {
        this.plugin = plugin;
        this.name = name;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");

        this.requirements = new GoalRewardRequirement();
        loadFromFile(); // Load before modifying anything

        this.active = active;
        saveToFile(); // Now save the updated object
        goalsManager.addGoal(this);
    }

    // Core file operations
    private void loadFromFile() {
        if (goalFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(goalFile);
            requirements.setTime(config.getLong("requirements.time", Long.MAX_VALUE));
            requirements.setPermissions(new ArrayList<>(config.getStringList("requirements.permissions")));
            requirements.setPlaceholderConditions(new ArrayList<>(config.getStringList("requirements.placeholders")));
            goalMessage = config.getString("goal-message", getDefaultGoalMessage());
            goalSound = config.getString("goal-sound", getDefaultGoalSound());
            rewardPermissions = new ArrayList<>(config.getStringList("rewards.permissions"));
            rewardCommands = new ArrayList<>(config.getStringList("rewards.commands"));
            active = config.getBoolean("active", false);
        } else {
            goalMessage = getDefaultGoalMessage();
            goalSound = getDefaultGoalSound();
            rewardPermissions = new ArrayList<>();
            rewardCommands = new ArrayList<>();
        }
    }

    private void saveToFile() {
        try {
            if (!goalFile.exists()) {
                goalFile.getParentFile().mkdirs();
                goalFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();
            config.options().setHeader(Arrays.asList(
                    "GUIDE OF AVAILABLE OPTIONS:",
                    "---------------------------",
                    "goal-sound is played to a player if it reaches the time specified in this config.",
                    "A list of available sounds can be found here: https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                    "Replace '<VERSION>' in the link with your current minecraft version. If it doesn't work try with the ",
                    "latest update of your version (e.g. '1.19' doesn't work and you need to use '1.19.4')",
                    "---------------------------",
                    "goal-message is showed to a player if it reaches the time specified in this config.",
                    "Available placeholders: %TIME_REQUIRED%, %PLAYER_NAME%. %GOAL_NAME%",
                    "---------------------------",
                    "active determines whether this goal is enabled and being checked by the plugin",
                    "Set to 'true' to enable the goal and track player progress",
                    "Set to 'false' (default option) to disable the goal without deleting it",
                    "This is useful for:",
                    "* Temporarily disabling goals without removing them",
                    "* Testing new goals before making them live",
                    "* Managing seasonal or event-specific goals",
                    "---------------------------",
                    "requirements:",
                    "  time: Required playtime (in seconds) for the goal to be completed",
                    "   - Note: if time isn't set, it defaults to a very long number, it is intended!",
                    "  permissions: List of permissions that the player must have to complete this goal",
                    "  placeholders: List of placeholder conditions that must be met to complete this goal",
                    "---------------------------",
                    "reward:",
                    "  permissions: Permissions that will be granted to a player when they reach this goal",
                    "  commands: List of commands that will be executed when a player reaches this goal",
                    "  Available placeholders in commands: PLAYER_NAME"

            ));
            config.set("active", active);
            config.set("goal-sound", goalSound);
            config.set("goal-message", goalMessage);
            config.set("requirements.time", requirements.getTime());
            config.set("requirements.permissions", requirements.getPermissions());
            config.set("requirements.placeholders", requirements.getPlaceholderConditions());
            config.set("rewards.permissions", rewardPermissions);
            config.set("rewards.commands", rewardCommands);
            config.save(goalFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save goal file for " + name + ": " + e.getMessage());
        }
    }

    public void deleteFile() {
        if (goalFile.exists()) {
            if (!goalFile.delete()) {
                plugin.getLogger().warning("Failed to delete goal file for " + name);
            }
        }
    }

    // Default values
    private String getDefaultGoalSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultGoalMessage() {
        return "[&6PlayTime&eManager&f]&7 Congratulations &e%PLAYER_NAME%&7 you have completed a new goal!";
    }

    // Basic getters
    public String getName() {
        return name;
    }

    public GoalRewardRequirement getRequirements() {
        return requirements;
    }

    public String getGoalMessage() {
        return goalMessage;
    }

    public String getGoalSound() {
        return goalSound;
    }

    public boolean isActive() {
        return active;
    }

    public ArrayList<String> getRewardCommands() {
        return rewardCommands;
    }

    public ArrayList<String> getRewardPermissions() {
        return rewardPermissions;
    }

    public void rename(String newName) {
        File oldFile = this.goalFile;

        OnlineUsersManager.getInstance().reload();

        this.name = newName;

        File newFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + newName + ".yml");

        try {
            // Create parent directories if they don't exist
            if (!newFile.getParentFile().exists()) {
                newFile.getParentFile().mkdirs();
            }

            // If old file exists, move it to new location
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    // If rename fails, try to copy and delete
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(oldFile);
                    config.save(newFile);
                    oldFile.delete();
                }
            }

            // Save to ensure all data is current
            saveToFile();

            // Update the name in the database for all users
            plugin.getDatabase().updateGoalName(oldFile.getName().replace(".yml", ""), newName);

            oldFile.delete();

        } catch (IOException e) {
            plugin.getLogger().severe("Could not rename goal file from " + oldFile.getName() + " to " + newFile.getName() + ": " + e.getMessage());
        }
    }

    // Setters and modifiers
    public void setTime(long time) {
        this.requirements.setTime(time);
        saveToFile();
    }

    public void setGoalMessage(String goalMessage) {
        this.goalMessage = goalMessage;
        saveToFile();
    }

    public void setGoalSound(String goalSound) {
        this.goalSound = goalSound;
        saveToFile();
    }

    public void setActivation(boolean activation) {
        this.active = activation;
        saveToFile();
    }

    public void addCommand(String command) {
        rewardCommands.add(command);
        saveToFile();
    }

    public void removeCommand(String command) {
        rewardCommands.remove(command);
        saveToFile();
    }

    public void addPermission(String permission) {
        rewardPermissions.add(permission);
        saveToFile();
    }

    public void removePermission(String permission) {
        rewardPermissions.remove(permission);
        saveToFile();
    }

    public void addRequirementPermission(String permission) {
        requirements.addPermission(permission);
        saveToFile();
    }

    public void removeRequirementPermission(String permission) {
        requirements.removePermission(permission);
        saveToFile();
    }

    public void addPlaceholderCondition(String condition) {
        requirements.addPlaceholderCondition(condition);
        saveToFile();
    }

    public void removePlaceholderCondition(String condition) {
        requirements.removePlaceholderCondition(condition);
        saveToFile();
    }

    public void clearPlaceholderConditions(){
        getRequirements().getPlaceholderConditions().clear();
        saveToFile();
    }

    public void clearRequirementPermissions(){
        getRequirements().getPermissions().clear();
        saveToFile();
    }

    public void kill() {
        goalsManager.removeGoal(this);
        DBUsersManager.getInstance().removeGoalFromAllUsers(name);
        deleteFile();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Goal goal = (Goal) o;
        return Objects.equals(name, goal.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Goal{" +
                "name='" + name + '\'' +
                ", time=" + requirements.getTime() +
                ", active=" + active +
                ", requirementPermissions=" + requirements.getPermissions().size() +
                ", placeholderConditions=" + requirements.getPlaceholderConditions().size() +
                ", rewardPermissions=" + rewardPermissions.size() +
                ", commands=" + rewardCommands.size() +
                ", message='" + goalMessage + '\'' +
                ", sound='" + goalSound + '\'' +
                '}';
    }

}