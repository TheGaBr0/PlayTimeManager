package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class StreakTracker {
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public StreakTracker() {}

    public void incrementAbsoluteStreak(OnlineUser user) {
        user.incrementAbsoluteJoinStreak();
    }

    public void incrementRelativeStreak(OnlineUser user) {
        user.incrementRelativeJoinStreak();
    }

    public void resetStreaks(OnlineUser user) {
        user.resetJoinStreaks();
    }

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

                // Track when all async operations finish
                if (processedCount.incrementAndGet() == totalPlayers) {
                    callback.accept(playersReset.get());
                }
            });
        }
    }


    public void restartUserJoinStreakRewards(DBUser user) {
        ArrayList<RewardSubInstance> userReceivedRewards = user.getReceivedRewards();
        for (RewardSubInstance subInstance : userReceivedRewards) {
            user.unreceiveReward(subInstance);
        }
        user.migrateUnclaimedRewards();
        user.resetRelativeJoinStreak();
    }
}