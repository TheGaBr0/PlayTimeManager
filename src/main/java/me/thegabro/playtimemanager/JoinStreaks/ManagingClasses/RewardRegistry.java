package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RewardRegistry {

    private static RewardRegistry instance;

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    // Master set of all loaded rewards
    private final Set<JoinStreakReward> rewards = new HashSet<>();

    // Flat list of sub-instances, one entry per join count per reward (expanded from ranges)
    private final ArrayList<RewardSubInstance> joinRewardsInstances = new ArrayList<RewardSubInstance>();

    // The reward with the highest max required joins — used to detect when a cycle should restart
    private JoinStreakReward lastRewardByJoins;

    private RewardRegistry() {}

    public static RewardRegistry getInstance() {
        if (instance == null) {
            instance = new RewardRegistry();
        }
        return instance;
    }

    public void createRewardsDirectory() {
        File rewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (!rewardsFolder.exists()) {
            rewardsFolder.mkdirs();
        }

        createWarningFile(rewardsFolder);
    }

    /** Drops a plain-text warning in the Rewards folder reminding admins not to rename reward files. */
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

    /** Scans the Rewards folder and loads every valid .yml file as a {@link JoinStreakReward}. */
    public void loadRewards() {
        rewards.clear();
        joinRewardsInstances.clear();

        File rewardsFolder = new File(plugin.getDataFolder(), "Rewards");
        if (rewardsFolder.exists() && rewardsFolder.isDirectory()) {
            File[] rewardFiles = rewardsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (rewardFiles != null) {
                for (File file : rewardFiles) {
                    String rewardID = file.getName().replace(".yml", "");
                    try {
                        JoinStreakReward reward = new JoinStreakReward(Integer.parseInt(rewardID), -1);
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

        // If no rewards remain, disable the schedule automatically
        if (rewards.isEmpty()) {
            plugin.getConfiguration().set("rewards-check-schedule-activation", false);
        }

        joinRewardsInstances.removeIf(r -> r.mainInstanceID() == reward.getId());
        updateEndLoopReward();
    }

    /**
     * Refreshes the {@link RewardSubInstance} entries for a reward in the flat instances list.
     * Removes any existing entries for the reward, then re-expands its current join range.
     * A reward covering joins 3–6 produces four sub-instances (3, 4, 5, 6).
     * Rewards with a required-joins of -1 (disabled) are skipped entirely.
     */
    public void updateJoinRewardsMap(JoinStreakReward reward) {
        joinRewardsInstances.removeIf(r -> r.mainInstanceID() == reward.getId());

        int minJoins = reward.getMinRequiredJoins();
        int maxJoins = reward.getMaxRequiredJoins();

        if (minJoins == -1) {
        } else if (minJoins == maxJoins) {
            joinRewardsInstances.add(new RewardSubInstance(reward.getId(), minJoins, false));
        } else {
            for (int joinCount = minJoins; joinCount <= maxJoins; joinCount++) {
                joinRewardsInstances.add(new RewardSubInstance(reward.getId(), joinCount, false));
            }
        }
    }

    /** Recalculates which reward has the highest max required joins. Called after any add/remove. */
    public void updateEndLoopReward() {
        lastRewardByJoins = rewards.stream()
                .filter(reward -> reward.getMinRequiredJoins() != -1)
                .max(Comparator.comparingInt(JoinStreakReward::getMaxRequiredJoins))
                .orElse(null);
    }

    /**
     * Returns the sub-instances that should be processed for a given join count.
     *
     * Already-received rewards are filtered out. Additionally, if a player has multiple
     * unclaimed rewards from previous cycles, only the one matching the current join count
     * is kept — preventing a flood of notifications for stale rewards.
     */
    public ArrayList<RewardSubInstance> getRewardIdsForJoinCount(int joinCount, OnlineUser onlineUser) {
        ArrayList<RewardSubInstance> rewardIds = new ArrayList<>();

        for (RewardSubInstance subInstance : joinRewardsInstances) {
            JoinStreakReward reward = getReward(subInstance.mainInstanceID());

            if (reward == null) continue;

            if (joinCount >= subInstance.requiredJoins() && subInstance.requiredJoins() != -1) {
                rewardIds.add(subInstance);
            }
        }

        // Strip rewards the player has already received
        List<RewardSubInstance> receivedRewards = onlineUser.getReceivedRewards();
        rewardIds.removeIf(reward ->
                receivedRewards.stream().anyMatch(receivedReward ->
                        receivedReward.mainInstanceID().equals(reward.mainInstanceID()) &&
                                receivedReward.requiredJoins().equals(reward.requiredJoins())
                )
        );

        // If a player has many unclaimed rewards from past cycles (e.g. 1.1 through 1.10),
        // only surface the one matching the current join count to avoid spamming them.
        List<RewardSubInstance> unclaimedRewards = onlineUser.getRewardsToBeClaimed();
        rewardIds.removeIf(reward ->
                unclaimedRewards.stream().anyMatch(unclaimedReward ->
                        unclaimedReward.mainInstanceID().equals(reward.mainInstanceID()) &&
                                unclaimedReward.requiredJoins().equals(reward.requiredJoins())
                ) && reward.requiredJoins() != joinCount
        );

        return rewardIds;
    }

    public JoinStreakReward getReward(int id) {
        return rewards.stream()
                .filter(g -> g.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public RewardSubInstance getSubInstance(Integer mainInstanceID, Integer requiredJoins) {
        for (RewardSubInstance subInstance : joinRewardsInstances) {
            if (subInstance.mainInstanceID().equals(mainInstanceID) &&
                    subInstance.requiredJoins().equals(requiredJoins)) {
                return subInstance;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return rewards.isEmpty();
    }

    public Set<JoinStreakReward> getRewards() {
        return new HashSet<>(rewards);
    }

    public void clearRewards() {
        rewards.clear();
        joinRewardsInstances.clear();
        lastRewardByJoins = null;
    }

    /** Returns the next available reward ID (current max + 1). */
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

    public ArrayList<RewardSubInstance> getJoinRewardsMap() {
        return joinRewardsInstances;
    }
}