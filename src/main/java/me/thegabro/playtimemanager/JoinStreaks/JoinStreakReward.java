package me.thegabro.playtimemanager.JoinStreaks;

import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class JoinStreakReward {
    private final PlayTimeManager plugin;
    private final JoinStreaksManager rewardsManager = JoinStreaksManager.getInstance();
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

    public JoinStreakReward(PlayTimeManager plugin, int id, int requiredJoins) {
        this.plugin = plugin;
        this.id = id;
        this.requiredJoinsRange = new int[]{requiredJoins, requiredJoins}; // Initialize as single value
        this.rewardFile = new File(plugin.getDataFolder() + File.separator + "Rewards" + File.separator + id + ".yml");
        loadFromFile();
        saveToFile();
        rewardsManager.getRewardRegistry().addReward(this);
    }


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
                        } catch (NumberFormatException ignored) {
                            // If parsing fails, keep the existing value
                        }
                    }
                }
            }

            rewardMessage = config.getString("reward-message", getDefaultRewardMessage());
            rewardSound = config.getString("reward-sound", getDefaultRewardSound());
            description = config.getString("description", "");
            rewardDescription = config.getString("reward-description", "");
            permissions = new ArrayList<>(config.getStringList("permissions"));
            commands = new ArrayList<>(config.getStringList("commands"));
            itemIcon = config.getString("item-icon", Material.SUNFLOWER.toString());
        } else {
            rewardMessage = getDefaultRewardMessage();
            rewardSound = getDefaultRewardSound();
            permissions = new ArrayList<>();
            commands = new ArrayList<>();
            description = "";
            rewardDescription = "";
            itemIcon = Material.SUNFLOWER.toString();
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
                    "required-joins-range specifies the range of joins for which this reward is active.",
                    "Format: 'min-max' (e.g., '5-10' means the reward is active for 5th through 10th joins)",
                    "For a single join count, use the same number twice (e.g., '5-5' for just the 5th join)",
                    "---------------------------",
                    "reward-sound is played to a player if it reaches the join streak specified in this config.",
                    "A list of available sounds can be found here: https://jd.papermc.io/paper/<VERSION>/org/bukkit/Sound.html",
                    "Replace '<VERSION>' in the link with your current minecraft version.",
                    "---------------------------",
                    "reward-message is showed to a player if it reaches the join streak specified in this config.",
                    "Available placeholders: %REQUIRED_JOINS%, %PLAYER_NAME%",
                    "---------------------------",
                    "description provides a short text description of the reward.",
                    "---------------------------",
                    "reward-description provides detailed information about the reward.",
                    "---------------------------",
                    "item-icon represents the visual representation of the reward in GUI.",
                    "---------------------------",
                    "permissions defines what permissions will be granted to a player when they reach this reward",
                    "You can specify multiple permissions and groups that will all be granted.",
                    "---------------------------",
                    "commands defines a list of commands that will be executed when a player reaches this reward",
                    "Available placeholders: PLAYER_NAME",
                    "Example commands:",
                    "- '/give PLAYER_NAME diamond 64'",
                    "- '/broadcast PLAYER_NAME has reached an amazing join streak!'"
            ));

            // Save join requirements in the new format
            config.set("required-joins-range", requiredJoinsRange[0] + "-" + requiredJoinsRange[1]);

            config.set("reward-sound", rewardSound);
            config.set("reward-message",rewardMessage);
            config.set("description", description);
            config.set("reward-description", rewardDescription);
            config.set("permissions", permissions);
            config.set("commands", commands);
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
        return plugin.getConfiguration().getPluginPrefix()+" Congratulations &e%PLAYER_NAME%&7, you have redeemed your reward successfully!";
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

    public boolean isSingleJoinReward() {
        return requiredJoinsRange[0] == requiredJoinsRange[1];
    }

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

    public ArrayList<String> getPermissions() {
        return permissions;
    }

    public void setRequiredJoinsRange(int minJoins, int maxJoins) {

        if (maxJoins < minJoins) {
            maxJoins = minJoins;
        }

        this.requiredJoinsRange = new int[]{minJoins, maxJoins};
        saveToFile();
    }

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
            // Single number
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

    // Setters for new fields
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

    public void kill() {
        rewardsManager.getRewardRegistry().removeReward(this);
        DBUsersManager.getInstance().removeRewardFromAllUsers(String.valueOf(id));
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