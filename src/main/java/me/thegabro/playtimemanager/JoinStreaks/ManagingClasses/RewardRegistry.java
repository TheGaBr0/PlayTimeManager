package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RewardRegistry {
    private final PlayTimeManager plugin;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final Map<Integer, LinkedHashSet<String>> joinRewardsMap = new HashMap<>();
    private JoinStreakReward lastRewardByJoins;

    public RewardRegistry(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void createRewardsDirectory() {
        File rewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (!rewardsFolder.exists()) {
            rewardsFolder.mkdirs();
        }

        createWarningFile(rewardsFolder);
    }

    private void createWarningFile(File rewardsFolder) {
        File warningFile = new File(rewardsFolder, "NEVER RENAME FILES IN THIS FOLDER.txt");
        if (!warningFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(warningFile))) {
                writer.write("NEVER RENAME FILES IN THIS FOLDER\n");
                writer.write("--------------------------------------------------\n");
                writer.write("WARNING: The files in this folder are named according to their ID.\n");
                writer.write("Changing the names of these files will cause the configuration files to be missing in the database.\n");
                writer.write("This could result in failures as IDs are used by the plugin, and modifying the file names will break the mapping.\n");
                writer.write("ID values should never be changed by the user.\n");
                writer.write("--------------------------------------------------");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadRewards() {
        rewards.clear();
        joinRewardsMap.clear();

        File rewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (rewardsFolder.exists() && rewardsFolder.isDirectory()) {
            File[] rewardFiles = rewardsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (rewardFiles != null) {
                for (File file : rewardFiles) {
                    String rewardID = file.getName().replace(".yml", "");
                    try {
                        JoinStreakReward reward = new JoinStreakReward(plugin, Integer.parseInt(rewardID), -1);
                        addReward(reward);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid reward file name: " + file.getName());
                    }
                }
            }
        }
    }

    public void addReward(JoinStreakReward reward) {
        rewards.add(reward);
        updateJoinRewardsMap(reward);
        updateEndLoopReward();
    }

    public void removeReward(JoinStreakReward reward) {
        rewards.remove(reward);

        if (rewards.isEmpty()) {
            plugin.getConfiguration().set("rewards-check-schedule-activation", false);
        }

        joinRewardsMap.remove(reward.getId());
        updateEndLoopReward();
    }

    public void updateJoinRewardsMap(JoinStreakReward reward) {
        String rewardIdString = String.valueOf(reward.getId());
        int minJoins = reward.getMinRequiredJoins();
        int maxJoins = reward.getMaxRequiredJoins();

        // First, remove any existing entries for this reward
        joinRewardsMap.entrySet().removeIf(entry -> entry.getValue().stream()
                .anyMatch(val -> val.split("\\.")[0].equals(rewardIdString)));

        if (minJoins == -1) {
            joinRewardsMap.put(reward.getId(), new LinkedHashSet<>());
        }
        else if (minJoins == maxJoins) {
            joinRewardsMap.put(reward.getId(), new LinkedHashSet<>(Collections.singleton(rewardIdString + "." + minJoins)));
        }
        else {
            LinkedHashSet<String> set = new LinkedHashSet<>();

            for (int joinCount = minJoins; joinCount <= maxJoins; joinCount++) {
                String joinCountStr = String.valueOf(joinCount);
                String valueStr = rewardIdString + "." + joinCountStr;

                set.add(valueStr);
            }
            joinRewardsMap.put(reward.getId(), set);
        }
    }

    public void updateEndLoopReward() {
        // Find the reward with the highest required joins
        lastRewardByJoins = rewards.stream()
                .filter(reward -> reward.getMinRequiredJoins() != -1) // Exclude rewards with no join requirement
                .max(Comparator.comparingInt(JoinStreakReward::getMaxRequiredJoins))
                .orElse(null);
    }

    public JoinStreakReward getMainInstance(String instance) {
        if (instance == null || instance.isEmpty()) {
            return null;
        }

        try {
            String[] parts = instance.split("\\.");
            if (parts.length > 0) {
                return getReward(Integer.parseInt(parts[0]));
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid reward ID format: " + instance);
        }

        return null;
    }

    public LinkedHashSet<String> getRewardIdsForJoinCount(int joinCount, OnlineUser onlineUser) {
        LinkedHashSet<String> rewardIds = new LinkedHashSet<>();

        for (Map.Entry<Integer, LinkedHashSet<String>> entry : joinRewardsMap.entrySet()) {
            int id = entry.getKey();
            JoinStreakReward reward = getReward(id);

            if (reward == null) continue;

            // Get min and max join count for this reward
            int minJoins = reward.getMinRequiredJoins();
            int maxJoins = reward.getMaxRequiredJoins();

            if (reward.isSingleJoinReward()) {
                // For single join rewards, check if join count meets the exact requirement
                if (joinCount >= minJoins && minJoins != -1) {
                    rewardIds.add(entry.getValue().iterator().next());
                }
            } else {
                // For interval rewards (e.g., 17-21), add rewards based on position in interval
                if (joinCount >= maxJoins) {
                    // User has exceeded the max interval, give all rewards
                    rewardIds.addAll(entry.getValue());
                } else if (joinCount >= minJoins) {
                    // User is within interval, give rewards for their current position and below
                    int position = joinCount - minJoins;
                    Iterator<String> iterator = entry.getValue().iterator();
                    int count = 0;

                    while (iterator.hasNext() && count <= position) {
                        rewardIds.add(iterator.next());
                        count++;
                    }
                }
            }
        }

        // Filter out rewards the user already has
        filterAlreadyProcessedRewards(rewardIds, onlineUser);

        return rewardIds;
    }

    private void filterAlreadyProcessedRewards(LinkedHashSet<String> rewardIds, OnlineUser onlineUser) {
        // Filter out rewards the user already has
        rewardIds.removeAll(onlineUser.getReceivedRewards());

        // Improved handling of .R duplicates
        Set<String> toRemove = new HashSet<>();
        for (String reward : onlineUser.getRewardsToBeClaimed()) {
            if (reward.endsWith(".R")) {
                String baseId = reward.substring(0, reward.length() - 2);
                String[] parts = baseId.split("\\.");

                // If second digit doesn't match current joinCount, remove the base ID too
                if (parts.length >= 2 && Integer.parseInt(parts[1]) != onlineUser.getRelativeJoinStreak()) {
                    toRemove.add(baseId);
                }
            } else {
                toRemove.add(reward);
            }
        }

        rewardIds.removeAll(toRemove);
        rewardIds.removeAll(onlineUser.getRewardsToBeClaimed());
    }

    public JoinStreakReward getReward(int id) {
        return rewards.stream()
                .filter(g -> g.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public boolean isEmpty() {
        return rewards.isEmpty();
    }

    public Set<JoinStreakReward> getRewards() {
        return new HashSet<>(rewards);
    }

    public void clearRewards() {
        rewards.clear();
        joinRewardsMap.clear();
        lastRewardByJoins = null;
    }

    public int getNextRewardId() {
        return rewards.stream()
                .mapToInt(JoinStreakReward::getId)
                .max()
                .orElse(0) + 1;
    }

    public JoinStreakReward getLastRewardByJoins() {
        return lastRewardByJoins;
    }

    public void cleanUp() {
        clearRewards();
    }

    public Map<Integer, LinkedHashSet<String>> getJoinRewardsMap() {
        return joinRewardsMap;
    }
}