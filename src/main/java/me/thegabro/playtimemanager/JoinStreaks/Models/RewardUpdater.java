package me.thegabro.playtimemanager.JoinStreaks.Models;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RewardUpdater {

    private static RewardUpdater instance;

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final RewardRegistry rewardRegistry = RewardRegistry.getInstance();

    private RewardUpdater() {}

    public static RewardUpdater getInstance() {
        if (instance == null) {
            instance = new RewardUpdater();
        }
        return instance;
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

    public void rewardsUpdater(){

        Set<JoinStreakReward> joinStreakRewardsClone = new HashSet<>(rewardRegistry.getRewards());
        for(JoinStreakReward j : joinStreakRewardsClone){
            File oldConfig = j.getRewardFile();
            int rewardID = j.getId();
            FileConfiguration config = YamlConfiguration.loadConfiguration(oldConfig);

            Map<String, Object> backup = createConfigBackup(config);

            j.kill(true);

            JoinStreakReward newRewardInstance = new JoinStreakReward(rewardID, -1);
            rewardRegistry.addReward(newRewardInstance);

            FileConfiguration newFileConfig = YamlConfiguration.loadConfiguration(newRewardInstance.getRewardFile());
            restoreFromBackup(backup, newFileConfig);
            newRewardInstance.saveToFile();
        }
    }

}
