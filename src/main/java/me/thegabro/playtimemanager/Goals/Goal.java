package me.thegabro.playtimemanager.Goals;

import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Goal {
    // Fields
    private final PlayTimeManager plugin;
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private String name;
    private long time;
    private final File goalFile;
    private ArrayList<String> permissions = new ArrayList<>();
    private ArrayList<String> commands = new ArrayList<>();
    private String goalMessage;
    private String goalSound;
    private boolean active;

    // Constructor
    public Goal(PlayTimeManager plugin, String name, Long time, boolean active) {
        this.plugin = plugin;
        this.name = name;
        this.time = time == null ? Long.MAX_VALUE : time;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");
        this.active = active;
        loadFromFile();
        saveToFile();
        goalsManager.addGoal(this);
    }

    // Core file operations
    private void loadFromFile() {
        if (goalFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(goalFile);
            time = config.getLong("time", time);
            goalMessage = config.getString("goal-message", getDefaultGoalMessage());
            goalSound = config.getString("goal-sound", getDefaultGoalSound());
            permissions = new ArrayList<>(config.getStringList("permissions"));
            commands = new ArrayList<>(config.getStringList("commands"));
            active = config.getBoolean("active", false);
        } else {
            goalMessage = getDefaultGoalMessage();
            goalSound = getDefaultGoalSound();
            permissions = new ArrayList<>();
            commands = new ArrayList<>();
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
                    "permissions defines what permissions will be granted to a player when they reach this goal",
                    "You can specify multiple permissions and groups that will all be granted. The plugin will assume that",
                    "the group has already been created using the permissions manager plugin specified in the main config.",
                    "---------------------------",
                    "commands defines a list of commands that will be executed when a player reaches this goal",
                    "Available placeholders: %PLAYER_NAME%",
                    "Example commands:",
                    "- '/give %PLAYER_NAME% diamond 64'",
                    "- '/broadcast %PLAYER_NAME% has reached an amazing milestone!'"
            ));
            config.set("time", time);
            config.set("goal-sound", goalSound);
            config.set("goal-message", goalMessage);
            config.set("active", active);
            config.set("permissions", permissions);
            config.set("commands", commands);
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
        return "[§6PlayTime§eManager§f]§7 Congratulations §e%PLAYER_NAME%§7 you have reached §6%TIME_REQUIRED%§7 of playtime!";
    }

    // Basic getters
    public String getName() {
        return name;
    }

    public long getTime() {
        return time;
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

    public ArrayList<String> getCommands() {
        return commands;
    }

    public ArrayList<String> getPermissions() {
        return permissions;
    }

    public void rename(String newName) {
        File oldFile = this.goalFile;

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
        this.time = time;
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

    // Management methods
    public void kill() {
        DBUsersManager.getInstance().removeGoalFromAllUsers(name);
        goalsManager.removeGoal(this);
        deleteFile();
    }

    // Object overrides
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
                ", time=" + time +
                ", active=" + active +
                ", permissions=" + permissions.size() +
                ", commands=" + commands.size() +
                ", message='" + goalMessage + '\'' +
                ", sound='" + goalSound + '\'' +
                '}';
    }
}