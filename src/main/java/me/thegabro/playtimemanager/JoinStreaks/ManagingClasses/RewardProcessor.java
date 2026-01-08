package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class RewardProcessor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final RewardRegistry rewardRegistry;
    private final StreakTracker streakTracker;
    private final RewardExecutor rewardExecutor;
    private final RewardMessageService messageService;

    public RewardProcessor(
            RewardRegistry rewardRegistry,
            StreakTracker streakTracker,
            RewardExecutor rewardExecutor,
            RewardMessageService messageService
    ) {
        this.rewardRegistry = rewardRegistry;
        this.streakTracker = streakTracker;
        this.rewardExecutor = rewardExecutor;
        this.messageService = messageService;
    }

    public void processEligibleRewards(OnlineUser onlineUser) {
        int currentStreak = onlineUser.getRelativeJoinStreak();

        // Get all rewards that match this specific join count
        ArrayList<RewardSubInstance> unclaimedRewards = rewardRegistry.getRewardIdsForJoinCount(currentStreak, onlineUser);

        for (RewardSubInstance subInstance : unclaimedRewards) {
            JoinStreakReward mainInstance = rewardRegistry.getReward(subInstance.mainInstanceID());

            if (onlineUser.isExpired(subInstance)) {
                messageService.sendRewardRelatedMessage(onlineUser, subInstance,
                        plugin.getConfiguration().getString("join-unclaimed-previous-message",
                                "[&6PlayTime&eManager&f]&7 &e%PLAYER_NAME%&7, you've joined &6%REQUIRED_JOINS%&7 " +
                                        "consecutive times, but you didn't claim your previous reward! Please use &e/claimrewards&7 " +
                                        "to collect your pending rewards before new ones can be granted."), 0);
                continue;
            }

            if (mainInstance != null) {
                processQualifiedReward(onlineUser, subInstance);
            }
        }

        // Check if we need to restart reward cycles
        JoinStreakReward lastReward = rewardRegistry.getLastRewardByJoins();
        if (lastReward != null && onlineUser.getRelativeJoinStreak() >= lastReward.getMaxRequiredJoins()) {
            streakTracker.restartUserJoinStreakRewards(onlineUser);
        }
    }

    private void processQualifiedReward(OnlineUser onlineUser, RewardSubInstance subInstance) {
        Player player = onlineUser.getPlayerInstance();
        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {

            messageService.sendRewardRelatedMessage(onlineUser, subInstance,
                    plugin.getConfiguration().getString("join-warn-autoclaim-message", "[&6PlayTime&eManager&f]&7 " +
                            "Great job, &e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 consecutive times and " +
                            "unlocked a new reward! We have automatically claimed it for you!"), 1);

            rewardExecutor.processCompletedReward(player, subInstance);
        } else {
            if (!onlineUser.getRewardsToBeClaimed().contains(subInstance)) {
                onlineUser.addRewardToBeClaimed(subInstance);
            }

            messageService.sendRewardRelatedMessage(onlineUser, subInstance,
                    plugin.getConfiguration().getString("join-warn-claim-message", "[&6PlayTime&eManager&f]&7 " +
                            "Great job, &e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 consecutive times and " +
                            "unlocked a new reward! Use &e/claimrewards&7 to collect it!"), 1);
        }
    }
}
