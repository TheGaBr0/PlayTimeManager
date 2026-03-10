package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class RewardProcessor {
    private static RewardProcessor instance;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final RewardRegistry rewardRegistry;
    private final StreakTracker streakTracker;
    private final RewardExecutor rewardExecutor;
    private final RewardMessageService messageService;

    private RewardProcessor(
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

    /**
     * Creates and returns the singleton instance.
     * Must be called once at startup before any call to {@link #getInstance()}.
     */
    public static RewardProcessor initialize(
            RewardRegistry rewardRegistry,
            StreakTracker streakTracker,
            RewardExecutor rewardExecutor,
            RewardMessageService messageService
    ) {
        if (instance == null) {
            instance = new RewardProcessor(rewardRegistry, streakTracker, rewardExecutor, messageService);
        }
        return instance;
    }

    public static RewardProcessor getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RewardProcessor has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /** Returns true if the player has already claimed this specific sub-instance. */
    private boolean hasAlreadyReceived(OnlineUser onlineUser, RewardSubInstance subInstance) {
        return onlineUser.getReceivedRewards().stream()
                .anyMatch(r -> r.mainInstanceID().equals(subInstance.mainInstanceID())
                        && r.requiredJoins().equals(subInstance.requiredJoins()));
    }

    /**
     * Evaluates all rewards that apply to the player's current join streak and handles each one.
     *
     * For expired rewards (unclaimed from a previous cycle), the player is notified to use
     * /claimrewards before new rewards can be granted. For fresh rewards, the player either
     * receives them automatically (if they have the autoclaim permission) or is prompted to
     * claim them manually.
     *
     * Once the player's streak reaches the last defined reward, the cycle restarts.
     */
    public void processEligibleRewards(OnlineUser onlineUser) {
        int currentStreak = onlineUser.getRelativeJoinStreak();

        ArrayList<RewardSubInstance> unclaimedRewards = rewardRegistry.getRewardIdsForJoinCount(currentStreak, onlineUser);

        for (RewardSubInstance subInstance : unclaimedRewards) {
            JoinStreakReward mainInstance = rewardRegistry.getReward(subInstance.mainInstanceID());

            if (mainInstance == null) continue;

            // Non-repeatable rewards the player has already received are skipped permanently
            if (!mainInstance.isRepeatable() && hasAlreadyReceived(onlineUser, subInstance)) {
                continue;
            }

            if (onlineUser.isRewardExpired(subInstance)) {
                messageService.sendRewardRelatedMessage(onlineUser, subInstance,
                        plugin.getConfiguration().getString("join-unclaimed-previous-message",
                                "[&6PlayTime&eManager&f]&7 &e%PLAYER_NAME%&7, you've joined &6%REQUIRED_JOINS%&7 " +
                                        "consecutive times, but you didn't claim your previous reward! Please use &e/claimrewards&7 " +
                                        "to collect your pending rewards before new ones can be granted."), 0);
                continue;
            }
            processQualifiedReward(onlineUser, subInstance);
        }

        // If the player has reached the end of the reward chain, restart the cycle
        JoinStreakReward lastReward = rewardRegistry.getLastRewardByJoins();
        if (lastReward != null && onlineUser.getRelativeJoinStreak() >= lastReward.getMaxRequiredJoins()) {
            streakTracker.restartUserJoinStreakRewards(onlineUser);
        }
    }

    /**
     * Handles a single reward the player has qualified for.
     * Auto-claims it if they have the required permission, otherwise queues it for manual claim.
     */
    private void processQualifiedReward(OnlineUser onlineUser, RewardSubInstance subInstance) {
        Player player = onlineUser.getPlayerInstance();

        if (player.hasPermission("playtime.joinstreak.claim.automatic")) {

            messageService.sendRewardRelatedMessage(onlineUser, subInstance,
                    plugin.getConfiguration().getString("join-warn-autoclaim-message", "[&6PlayTime&eManager&f]&7 " +
                            "Great job, &e%PLAYER_NAME%&7! You have joined &6%REQUIRED_JOINS%&7 consecutive times and " +
                            "unlocked a new reward! We have automatically claimed it for you!"), 1);

            rewardExecutor.processCompletedReward(onlineUser, subInstance);
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