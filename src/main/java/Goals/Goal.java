package Goals;

import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class Goal {

    private final PlayTimeManager plugin;
    private final String name;
    private long time;
    private String LPGroup;
    private final File goalFile;

    private String goalMessage;
    private String goalSound;

    public Goal(PlayTimeManager plugin, String name, Long time, String LPGroup) {
        this.plugin = plugin;
        this.name = name;
        this.time = time == null ? Long.MAX_VALUE : time;
        this.LPGroup = LPGroup == null ? "None" : LPGroup;
        this.goalFile = new File(plugin.getDataFolder() + File.separator + "Goals" + File.separator + name + ".yml");
        loadFromFile();
        saveToFile();


        GoalManager.addGoal(this);
    }

    private void loadFromFile() {
        if (goalFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(goalFile);
            time = config.getLong("Time", time);
            LPGroup = config.getString("LuckPermsGroup", LPGroup);
            goalMessage = config.getString("goal-message", getDefaultGoalMessage());
            goalSound = config.getString("goal-sound", getDefaultGoalSound());
        } else {
            goalMessage = getDefaultGoalMessage();
            goalSound = getDefaultGoalSound();
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
                    "# GUIDE OF AVAILABLE OPTIONS:",
                    "# ---------------------------",
                    "# goal-sound is played to a player if it reaches the time specified in this config.",
                    "# A list of available sounds can be found here: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html",
                    "# ---------------------------",
                    "# goal-message is showed to a player if it reaches the time specified in this config.",
                    "# Available placeholders: %TIME_REQUIRED%, %GROUP_NAME%, %PLAYER_NAME%"
            ));
            config.set("Time", time);
            config.set("LuckPermsGroup", LPGroup);
            config.set("goal-sound", goalSound);
            config.set("goal-message", goalMessage);
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

    private String getDefaultGoalSound() {
        return "ENTITY_PLAYER_LEVELUP";
    }

    private String getDefaultGoalMessage() {
        return "[§6PlayTime§eManager§f]§7 Congratulations §e%PLAYER_NAME%§7 you have reached §6%TIME_REQUIRED%§7 of playtime so you have been promoted to §e%GROUP_NAME%§7!";
    }

    public String getName() {
        return name;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
        saveToFile();
    }

    public String getLPGroup() {
        return LPGroup;
    }

    public void setLPGroup(String LPGroup) {
        this.LPGroup = LPGroup;
        saveToFile();
    }

    public String getGoalMessage(){
        return goalMessage;
    }

    public String getGoalSound(){
        return goalSound;
    }

    public void setGoalMessage(String goalMessage){
        this.goalMessage = goalMessage;
    }

    public void setGoalSound(String goalSound){
        this.goalSound = goalSound;
    }

    public void kill() {
        GoalManager.removeGoal(this);
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
                ", time=" + time +
                ", LPGroup='" + LPGroup + '\'' +
                '}';
    }
}