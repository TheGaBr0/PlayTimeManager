package me.thegabro.playtimemanager.Goals;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class GoalsManager {
    private static GoalsManager instance;
    private final Set<Goal> goals = new HashSet<>();
    private PlayTimeManager plugin;

    private GoalsManager() {}

    public static synchronized GoalsManager getInstance() {
        if (instance == null) {
            instance = new GoalsManager();
        }
        return instance;
    }

    public void initialize(PlayTimeManager playTimeManager) {
        this.plugin = playTimeManager;
        clearGoals();
        loadGoals();
    }

    public void processPlayerLogin(OnlineUser user){

        List<String> notReceivedGoals = new ArrayList<>(user.getNotReceivedGoals());

        for(String notReceivedGoal : notReceivedGoals){
            user.markGoalAsReceivedAsync(notReceivedGoal, () -> {
                Player player = user.getPlayerInstance();
                if (player != null && player.isOnline()) {
                    getGoal(notReceivedGoal).processCompletedGoal(user, player);
                }
            });
        }
    }

    public void addGoal(Goal goal) {
        goals.add(goal);
    }

    public void removeGoal(Goal goal) {
        goals.remove(goal);
    }

    public Goal getGoal(String name) {
        return goals.stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public Set<Goal> getGoals() {
        return new HashSet<>(goals);
    }

    public List<String> getGoalsNames() {
        return goals.stream().map(Goal::getName).collect(Collectors.toList());
    }

    public void clearGoals() {

        for(Goal g : goals){ //IMPORTANT
            g.cancelCheckTask();
        }

        goals.clear();
    }

    public void loadGoals() {
        File goalsFolder = new File(plugin.getDataFolder(), "Goals");
        if (goalsFolder.exists() && goalsFolder.isDirectory()) {
            File[] goalFiles = goalsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (goalFiles != null) {
                for (File file : goalFiles) {
                    String goalName = file.getName().replace(".yml", "");
                    addGoal(new Goal(plugin, goalName));
                }
            }
        }
    }

    public boolean areAllInactive() {
        for (Goal g : goals)
            if (g.isActive())
                return false;

        return true;
    }

    private Map<String, Object> createConfigBackup(FileConfiguration config) {
        Map<String, Object> backup = new HashMap<>();

        // Read all keys from current config
        Set<String> keys = config.getKeys(true);
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) {
                backup.put(key, value);
            }
        }

        return backup;
    }

    /**
     * Restores values from a backup, preserving user customizations
     * Only restores leaf values (not entire sections) to preserve new keys
     * @param backup Map containing the backed up values
     */
    public void restoreFromBackup(Map<String, Object> backup, FileConfiguration config) {
        if (backup == null || backup.isEmpty()) {
            plugin.getLogger().warning("Backup is null or empty, nothing to restore");
            return;
        }

        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            String key = entry.getKey();
            Object backupValue = entry.getValue();

            // Check if the key exists in the new config structure
            if (!config.contains(key)) {
                continue;
            }

            // CRITICAL: Skip MemorySection objects (nested sections)
            // These contain multiple keys and restoring them would overwrite entire sections
            if (backupValue instanceof org.bukkit.configuration.MemorySection) {
                continue;
            }

            // Get the current value from the new config (this is the default from the new file)
            Object currentValue = config.get(key);

            // Only restore leaf values (strings, numbers, booleans, lists, etc.)
            // Only restore if:
            // 1. The backup has a non-null value
            // 2. The backup value is different from the current default
            // 3. The backup value is not empty for strings
            if (backupValue != null && !backupValue.equals(currentValue)) {
                // Additional check for strings - don't restore empty strings
                if (backupValue instanceof String && ((String) backupValue).trim().isEmpty()) {
                    continue;
                }
                config.set(key, backupValue);
            }
        }
    }

    public void goalsUpdater(){

        Set<Goal> goalsClone = new HashSet<>(goals);
        for(Goal g : goalsClone){
            File oldConfig = g.getGoalFile();
            String goalName = g.getName();
            FileConfiguration config = YamlConfiguration.loadConfiguration(oldConfig);

            Map<String, Object> backup = createConfigBackup(config);

            g.kill(true);

            Goal newGoalInstance = new Goal(plugin, goalName);
            goals.add(newGoalInstance);

            restoreFromBackup(backup, config);

            g.saveToFile();
        }
    }

    public synchronized void stop() {
        clearGoals();
        this.plugin = null;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }
}