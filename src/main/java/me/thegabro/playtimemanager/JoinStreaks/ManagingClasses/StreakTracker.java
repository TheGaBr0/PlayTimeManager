package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class StreakTracker {
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final RewardRegistry rewardRegistry = RewardRegistry.getInstance();

    private static StreakTracker instance;

    private StreakTracker() {}

    public static StreakTracker getInstance() {
        if (instance == null) {
            instance = new StreakTracker();
        }
        return instance;
    }

    /** Increments the all-time join streak for the given player. */
    public void incrementAbsoluteStreak(OnlineUser user) {
        user.incrementAbsoluteJoinStreak();
    }

    /** Increments the current-cycle join streak for the given player. */
    public void incrementRelativeStreak(OnlineUser user) {
        user.incrementRelativeJoinStreak();
    }

    /**
     * Asynchronously resets join streaks for players who have been inactive too long.
     * A player is considered inactive if they haven't been seen within {@code intervalSeconds * missesAllowed}.
     * Calls {@code callback} with the number of players whose streaks were reset once all are processed.
     */
    public void resetInactivePlayerStreaksAsync(Set<String> playersWithStreaks, long intervalSeconds, int missesAllowed, Consumer<Integer> callback) {
        AtomicInteger playersReset = new AtomicInteger();
        AtomicInteger processedCount = new AtomicInteger();

        int totalPlayers = playersWithStreaks.size();
        if (totalPlayers == 0) {
            callback.accept(0);
            return;
        }

        for (String playerUUID : playersWithStreaks) {
            dbUsersManager.getUserFromUUIDAsyncWithContext(playerUUID, "reset inactive player streak", user -> {
                if (user != null) {
                    Instant lastSeen = user.getLastSeen();

                    if (lastSeen == null) {
                        user.resetJoinStreaks();
                        playersReset.incrementAndGet();
                    } else {
                        long secondsSinceLastSeen = Duration.between(lastSeen, Instant.now()).getSeconds();
                        int effectiveMisses = Math.max(missesAllowed, 1);

                        if (secondsSinceLastSeen > intervalSeconds * effectiveMisses) {
                            user.resetJoinStreaks();
                            restartUserJoinStreakRewards(user);
                            playersReset.incrementAndGet();
                        }
                    }
                }

                // Fire the callback only after all async lookups have completed
                if (processedCount.incrementAndGet() == totalPlayers) {
                    callback.accept(playersReset.get());
                }
            });
        }
    }

    /**
     * Resets a user's reward progress at the end of a streak cycle.
     * Repeatable rewards are removed from receivedRewards so they can be earned again.
     * Non-repeatable rewards are intentionally left in place to block future claims.
     * Any unclaimed rewards are migrated to expired status, and the relative streak is reset.
     */
    public void restartUserJoinStreakRewards(DBUser user) {
        List<RewardSubInstance> userReceivedRewards = user.getReceivedRewards();
        for (RewardSubInstance subInstance : userReceivedRewards) {
            JoinStreakReward reward = rewardRegistry.getReward(subInstance.mainInstanceID());

            // Leave non-repeatable rewards in receivedRewards so rewardProcessor.hasAlreadyReceived() blocks them forever
            if (reward == null || !reward.isRepeatable()) continue;

            user.unreceiveReward(subInstance);
        }
        user.migrateUnclaimedRewards();
        user.resetRelativeJoinStreak();
    }
}