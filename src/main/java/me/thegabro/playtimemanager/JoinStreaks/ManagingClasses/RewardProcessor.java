package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.entity.Player;

import java.util.ArrayList;

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
        int currentStreak = onlineUser.getRelativeJoinStreak();

        // Get all rewards that match this specific join count
        ArrayList<RewardSubInstance> unclaimedRewards = rewardRegistry.getRewardIdsForJoinCount(currentStreak, onlineUser);

        for (RewardSubInstance subInstance : unclaimedRewards) {
            JoinStreakReward mainInstance = rewardRegistry.getReward(subInstance.mainInstanceID());

            if (onlineUser.isExpired(subInstance)) {
                messageService.sendRewardRelatedMessage(player, subInstance, plugin.getConfiguration().getString("join-unclaimed-previous-message"), 0);
                continue;
            }

            if (mainInstance != null) {
                processQualifiedReward(onlineUser, player, subInstance);
            }
        }

        // Check if we need to restart reward cycles
        JoinStreakReward lastReward = rewardRegistry.getLastRewardByJoins();
        if (lastReward != null && onlineUser.getRelativeJoinStreak() >= lastReward.getMaxRequiredJoins()) {
            streakTracker.restartUserJoinStreakRewards(onlineUser);
        }
    }

    private void processQualifiedReward(OnlineUser onlineUser, Player player, RewardSubInstance subInstance) {
        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {

            messageService.sendRewardRelatedMessage(player, subInstance, plugin.getConfiguration().getString("join-warn-autoclaim-message"), 1);

            rewardExecutor.processCompletedReward(player, subInstance);
        } else {
            if (!onlineUser.getRewardsToBeClaimed().contains(subInstance)) {
                onlineUser.addRewardToBeClaimed(subInstance);
            }

            messageService.sendRewardRelatedMessage(player, subInstance, plugin.getConfiguration().getString("join-warn-claim-message"), 1);
        }
    }
}
