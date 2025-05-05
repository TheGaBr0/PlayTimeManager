package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;

public class RewardProcessor {
    private final PlayTimeManager plugin;
    private final RewardRegistry rewardRegistry;
    private final StreakTracker streakTracker;
    private final RewardExecutor rewardExecutor;
    private final RewardMessageService messageService;

    public RewardProcessor(
            PlayTimeManager plugin,
            RewardRegistry rewardRegistry,
            StreakTracker streakTracker,
            RewardExecutor rewardExecutor,
            RewardMessageService messageService
    ) {
        this.plugin = plugin;
        this.rewardRegistry = rewardRegistry;
        this.streakTracker = streakTracker;
        this.rewardExecutor = rewardExecutor;
        this.messageService = messageService;
    }

    public void processEligibleRewards(OnlineUser onlineUser, Player player) {
        // Get the current join streak count
        int currentStreak = onlineUser.getRelativeJoinStreak();

        // Get all rewards that match this specific join count
        LinkedHashSet<String> unclaimedRewards = rewardRegistry.getRewardIdsForJoinCount(currentStreak, onlineUser);

        // Process each unclaimed reward
        for (String rewardKey : unclaimedRewards) {
            JoinStreakReward mainInstance = rewardRegistry.getMainInstance(rewardKey);

            if (onlineUser.getRewardsToBeClaimed().contains(rewardKey + ".R")) {
                messageService.sendRewardRelatedMessage(player, rewardKey, plugin.getConfiguration().getJoinCantClaimMessage(), 0);
                continue;
            }

            if (mainInstance != null) {
                processQualifiedReward(onlineUser, player, mainInstance, rewardKey);
            }
        }

        // Check if we need to restart reward cycles
        JoinStreakReward lastReward = rewardRegistry.getLastRewardByJoins();
        if (lastReward != null && onlineUser.getRelativeJoinStreak() >= lastReward.getMaxRequiredJoins()) {
            streakTracker.restartUserJoinStreakRewards(onlineUser);
        }
    }

    private void processQualifiedReward(OnlineUser onlineUser, Player player, JoinStreakReward reward, String rewardKey) {
        // Check if player has auto-claim permission
        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {
            // Auto claim the reward with the specific key (not just the integer ID)
            onlineUser.addReceivedReward(rewardKey);

            messageService.sendRewardRelatedMessage(player, rewardKey, plugin.getConfiguration().getJoinAutoClaimMessage(), 1);

            rewardExecutor.processCompletedReward(player, reward, rewardKey);
        } else {
            // Let's first check that the user doesn't have any unclaimed clones of this reward from a previous cycle
            if (!onlineUser.getRewardsToBeClaimed().contains(rewardKey.concat(".R"))) {
                // Add to pending rewards with the specific key
                onlineUser.addRewardToBeClaimed(rewardKey);
            }

            messageService.sendRewardRelatedMessage(player, rewardKey, plugin.getConfiguration().getJoinClaimMessage(), 1);
        }
    }
}
