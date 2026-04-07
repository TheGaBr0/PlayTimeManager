package me.thegabro.playtimemanager.JoinStreaks.Models;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single join streak reward definition, backed by a .yml file in the Rewards folder.
 *
 * A reward can target a single join count (e.g. exactly the 10th join) or a range
 * (e.g. joins 1 through 25). Range-based rewards are always repeatable because the
 * non-repeatable concept only makes sense for a single milestone.
 *
 * Changes to any field are persisted immediately by calling {@link #saveToFile()}.
 */
public class JoinStreakReward {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler databaseHandler = DatabaseHandler.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private final int id;
    private int[] requiredJoinsRange;
    private final File rewardFile;
    private ArrayList<String> permissions = new ArrayList<>();
    private ArrayList<String> commands = new ArrayList<>();
    private String rewardMessage;
    private String rewardSound;
    private String itemIcon;
    private String description;
    private String rewardDescription;
    private boolean repeatable;

    /**
     * Constructs a reward with the given ID.
     * If a matching .yml file exists it is loaded; otherwise defaults are applied and the file is created.
     *
     * @param id           unique numeric identifier (matches the file name)
     * @param requiredJoins initial join requirement — ignored if the file already exists
     */
    public JoinStreakReward(int id, int requiredJoins) {
        this.id = id;
        this.requiredJoinsRange = new int[]{requiredJoins, requiredJoins};
        this.rewardFile = new File(plugin.getDataFolder() + File.separator + "Rewards" + File.separator + id + ".yml");
        loadFromFile();
        saveToFile();
    }

    /** Reads all reward settings from the backing .yml file, or sets defaults if the file does not exist yet. */
    private void loadFromFile() {
        if (rewardFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(rewardFile);

            if (config.contains("required-joins-range")) {
                String rangeStr = config.getString("required-joins-range");
                if (rangeStr != null && rangeStr.contains("-")) {
                    String[] parts = rangeStr.split("-");
                    if (parts.length == 2) {
                        try {
                            int min = Integer.parseInt(parts[0]);
                            int max = Integer.parseInt(parts[1]);
                            if (min > 0 && max >= min) {
                                requiredJoinsRange = new int[]{min, max};
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            rewardMessage = config.getString("reward-message", getDefaultRewardMessage());
            rewardSound = config.getString("reward-sound", getDefaultRewardSound());
            description = config.getString("description", "");
            rewardDescription = config.getString("reward-description", "");
            permissions = new ArrayList<>(config.getStringList("permissions"));
            commands = new ArrayList<>(config.getStringList("commands"));
            repeatable = config.getBoolean("repeatable");
            itemIcon = config.getString("item-icon", Material.SUNFLOWER.toString());

            // Range-based rewards cannot be non-repeatable — enforce this on load
            if (!repeatable && requiredJoinsRange[0] != requiredJoinsRange[1]) {
                plugin.getLogger().warning("Reward " + id + ": 'repeatable' was false but required-joins-range is a range. Forcing repeatable=true.");
                repeatable = true;
            }
        } else {
            rewardMessage = getDefaultRewardMessage();
            rewardSound = getDefaultRewardSound();
            permissions = new ArrayList<>();
            commands = new ArrayList<>();
            repeatable = true;
            description = "";
            rewardDescription = "";
            itemIcon = Material.SUNFLOWER.toString();
        }
    }

    /** Writes all current field values to the backing .yml file, creating it if necessary. */
    protected void saveToFile() {
        try {
            if (!rewardFile.exists()) {
                rewardFile.getParentFile().mkdirs();
                rewardFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();
            config.options().setHeader(Arrays.asList(
                    "GUIDE OF AVAILABLE OPTIONS:",
                    "---------------------------",
                    "Available placeholders for reward-message, description and reward-description:",
                    "%REQUIRED_JOINS%, %PLAYER_NAME%, %JOIN_STREAK%",
                    "---------------------------",
                    "required-joins-range specifies the range of joins for which this reward is active.",
                    "Format: 'min-max' (e.g., '5-10' means the reward is active for 5th through 10th joins)",
                    "For a single join count, use the same number twice (e.g., '5-5' for just the 5th join)",
                    "---------------------------",
                    "reward-sound is played to a player if it reaches the join streak specified in this config.",
                    "A list of available sounds can be found here: https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                    "Replace '<VERSION>' in the link with your current minecraft version.",
                    "N.B. If your current version doesn't work, use the latest patch of the same major version. ",
                    "E.g. '1.19' doesn't work, use '1.19.4'.",
                    "---------------------------",
                    "reward-message is showed to a player if it reaches the join streak specified in this config.",
                    "---------------------------",
                    "description provides a short text description of the reward.",
                    "---------------------------",
                    "reward-description provides detailed information about the reward.",
                    "---------------------------",
                    "repeatable specifies whether this reward can be obtained multiple times by a player or only the first one.",
                    "NOTE: repeatable=false is only allowed when required-joins-range is a single value (e.g. '30-30').",
                    "If required-joins-range is a range (e.g. '1-25'), repeatable is automatically set to true.",
                    "---------------------------",
                    "item-icon represents the visual representation of the reward in GUI.",
                    "---------------------------",
                    "permissions defines what permissions will be granted to a player when they reach this reward",
                    "You can specify multiple permissions and groups that will all be granted.",
                    "---------------------------",
                    "commands defines a list of commands that will be executed when a player reaches this reward",
                    "Available placeholders for commands only: PLAYER_NAME",
                    "Example commands:",
                    "- '/give PLAYER_NAME diamond 64'",
                    "- '/broadcast PLAYER_NAME has reached an amazing join streak!'"
            ));

            config.set("required-joins-range", requiredJoinsRange[0] + "-" + requiredJoinsRange[1]);
            config.set("reward-sound", rewardSound);
            config.set("reward-message",rewardMessage);
            config.set("description", description);
            config.set("reward-description", rewardDescription);
            config.set("permissions", permissions);
            config.set("commands", commands);
            config.set("repeatable", repeatable);
            config.set("item-icon", itemIcon);
            config.save(rewardFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save reward file for " + id + ": " + e.getMessage());
        }
    }

    private String getDefaultRewardSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultRewardMessage() {
        return config.getString("prefix")+" Congratulations &e%PLAYER_NAME%&7, you have redeemed your reward successfully!";
    }

    public ItemStack getDefaultIcon(){
        return new ItemStack(Material.SUNFLOWER);
    }

    public int getId() {
        return id;
    }

    public int[] getRequiredJoinsRange() {
        return requiredJoinsRange;
    }

    public int getMinRequiredJoins() {
        return requiredJoinsRange[0];
    }

    public int getMaxRequiredJoins() {
        return requiredJoinsRange[1];
    }

    /** Returns true if this reward targets a single join count rather than a range. */
    public boolean isSingleJoinReward() {
        return requiredJoinsRange[0] == requiredJoinsRange[1];
    }

    /** Returns the join requirement as a display string, e.g. "10" or "1-25". */
    public String getRequiredJoinsDisplay() {
        if (isSingleJoinReward()) {
            return String.valueOf(requiredJoinsRange[0]);
        } else {
            return requiredJoinsRange[0] + "-" + requiredJoinsRange[1];
        }
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

    public boolean isRepeatable(){ return repeatable; }

    public ArrayList<String> getPermissions() {
        return permissions;
    }

    public File getRewardFile(){
        return rewardFile;
    }

    /**
     * Updates the join range and saves to file.
     * If min != max, repeatable is forced to true since range rewards cannot be one-time.
     */
    public void setRequiredJoinsRange(int minJoins, int maxJoins) {
        if (maxJoins < minJoins) {
            maxJoins = minJoins;
        }

        int oldMin = this.requiredJoinsRange[0];
        int oldMax = this.requiredJoinsRange[1];

        this.requiredJoinsRange = new int[]{minJoins, maxJoins};

        if (minJoins != maxJoins) {
            this.repeatable = true;
        }

        if (!repeatable) {
            databaseHandler.getStreakDAO().updateRequiredJoinsForReward(id, maxJoins);
        } else {
            databaseHandler.getStreakDAO().syncRangeRewardEntries(id, minJoins, maxJoins);
        }

        DBUsersManager.getInstance().updateRequiredJoinsForReward(id, oldMin, oldMax, minJoins, maxJoins);

        saveToFile();
    }

    /**
     * Parses a join range from a string and applies it.
     * Accepts a single integer ("10"), a range ("1-25"), or "-1" to disable the reward.
     * Returns false if the string is invalid or the values are out of range.
     */
    public boolean setRequiredJoinsFromString(String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) {
            return false;
        }

        if (rangeStr.equals("-1")) {
            setRequiredJoinsRange(-1, -1);
            return true;
        }

        if (rangeStr.contains("-")) {
            String[] parts = rangeStr.split("-");
            if (parts.length == 2) {
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());

                    if (min <= 0 || max < min) {
                        return false;
                    }

                    setRequiredJoinsRange(min, max);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        } else {
            try {
                int value = Integer.parseInt(rangeStr.trim());
                if (value <= 0) {
                    return false;
                }

                setRequiredJoinsRange(value, value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
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

    /**
     * Sets the repeatable flag. Silently ignored if the reward targets a range,
     * since range rewards must always be repeatable.
     */
    public void setRepeatable(boolean repeatable) {
        if (!repeatable && requiredJoinsRange[0] != requiredJoinsRange[1]) {
            return;
        }
        this.repeatable = repeatable;
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

    public String getItemIcon() {
        return itemIcon;
    }

    public String getDescription() {
        return description;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public void setItemIcon(String itemIcon) {
        this.itemIcon = itemIcon;
        saveToFile();
    }

    public void setDescription(String description) {
        this.description = description;
        saveToFile();
    }

    public void setRewardDescription(String rewardDescription) {
        this.rewardDescription = rewardDescription;
        saveToFile();
    }

    /**
     * Removes this reward from the registry and deletes its file.
     * If {@code update} is false, also wipes all player records associated with this reward.
     */
    public void kill(boolean update) {
        RewardRegistry.getInstance().removeReward(this);
        deleteFile();

        if(!update)
            DBUsersManager.getInstance().removeRewardFromAllUsers(id);
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
                ", requiredJoins=" + getRequiredJoinsDisplay() +
                ", permissions=" + permissions.size() +
                ", commands=" + commands.size() +
                ", message='" + rewardMessage + '\'' +
                ", sound='" + rewardSound + '\'' +
                ", description='" + description + '\'' +
                ", rewardDescription='" + rewardDescription + '\'' +
                ", hasItemIcon=" + (itemIcon != null) +
                '}';
    }
}