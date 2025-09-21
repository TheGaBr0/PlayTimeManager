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
    private final PlayTimeManager plugin;
    private final Set<JoinStreakReward> rewards = new HashSet<>();
    private final ArrayList<RewardSubInstance> joinRewardsInstances = new ArrayList<RewardSubInstance>();
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
        joinRewardsInstances.clear();

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

        joinRewardsInstances.removeIf(r -> r.mainInstanceID() == reward.getId());
        updateEndLoopReward();
    }

    public void updateJoinRewardsMap(JoinStreakReward reward) {

        int minJoins = reward.getMinRequiredJoins();
        int maxJoins = reward.getMaxRequiredJoins();
        //List<String> debugOutput = new ArrayList<>();

        if (minJoins == -1) {
            joinRewardsInstances.add(new RewardSubInstance(reward.getId(), -1, false));
            //debugOutput.add(reward.getId() + "." + -1);

        }
        else if (minJoins == maxJoins) {
            joinRewardsInstances.add(new RewardSubInstance(reward.getId(), minJoins, false));
            //debugOutput.add(reward.getId() + "." + minJoins);

        }
        else {
            for (int joinCount = minJoins; joinCount <= maxJoins; joinCount++) {
                joinRewardsInstances.add(new RewardSubInstance(reward.getId(), joinCount, false));
                //debugOutput.add(reward.getId() + "." + joinCount);

            }
        }
        //plugin.getLogger().info("SubInstances: " + String.join(" ", debugOutput));

    }

    public void updateEndLoopReward() {
        // Find the reward with the highest required joins
        lastRewardByJoins = rewards.stream()
                .filter(reward -> reward.getMinRequiredJoins() != -1) // Exclude rewards with no join requirement
                .max(Comparator.comparingInt(JoinStreakReward::getMaxRequiredJoins))
                .orElse(null);
    }

    public ArrayList<RewardSubInstance> getRewardIdsForJoinCount(int joinCount, OnlineUser onlineUser) {
        ArrayList<RewardSubInstance> rewardIds = new ArrayList<>();


        for (RewardSubInstance subInstance : joinRewardsInstances) {
            JoinStreakReward reward = getReward(subInstance.mainInstanceID());

            if (reward == null) continue;

            // For single join rewards, check if join count meets the exact requirement
            if (joinCount >= subInstance.requiredJoins() && subInstance.requiredJoins() != -1) {
                rewardIds.add(subInstance);
            }
        }

        // Filter out rewards the user already has
        ArrayList<RewardSubInstance> receivedRewards = onlineUser.getReceivedRewards();
        rewardIds.removeIf(reward ->
                receivedRewards.stream().anyMatch(receivedReward ->
                        receivedReward.mainInstanceID().equals(reward.mainInstanceID()) &&
                                receivedReward.requiredJoins().equals(reward.requiredJoins())
                )
        );

        //Without this part of code, supposing a player has 10 rewards unclaimed from previous cycles (from 1.1 to 1.10)
        // then it would receive a notification about all of them all at once. 10 notifications per check. Terrible.
        // We need to filter all the unclaimed rewards from previous cycle except the last one(s) (the one(s) that should be available
        // since the last check)
        ArrayList<RewardSubInstance> unclaimedRewards = onlineUser.getRewardsToBeClaimed();
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
        return null; // Return null if not found
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