package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

public class StreakTracker {
    private final PlayTimeManager plugin;
    private final DBUsersManager dbUsersManager;

    public StreakTracker(PlayTimeManager plugin, DBUsersManager dbUsersManager) {
        this.plugin = plugin;
        this.dbUsersManager = dbUsersManager;
    }

    public void incrementAbsoluteStreak(OnlineUser user) {
        user.incrementAbsoluteJoinStreak();
    }

    public void incrementRelativeStreak(OnlineUser user) {
        user.incrementRelativeJoinStreak();
    }

    public void resetStreaks(OnlineUser user) {
        user.resetJoinStreaks();
    }

    public int resetInactivePlayerStreaks(Set<String> playersWithStreaks, long intervalSeconds, int missesAllowed) {
        int playersReset = 0;

        for (String playerUUID : playersWithStreaks) {
            DBUser user = dbUsersManager.getUserFromUUIDWithContext(playerUUID, "reset inactive player streak");
            if (user != null) {
                // Check if the player's last seen time is older than the interval
                LocalDateTime lastSeen = user.getLastSeen();

                // Null or empty check first
                if (lastSeen == null) {
                    user.resetJoinStreaks();
                    playersReset++;
                    continue;
                }

                // Calculate seconds since last seen
                long secondsSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now()).getSeconds();

                //misses can't be lower than 0... otherwise it will reset immediately
                if(missesAllowed <=0)
                    missesAllowed = 1;

                // Reset if seconds since last seen is greater than interval
                if (secondsSinceLastSeen > intervalSeconds * missesAllowed) {
                    user.resetJoinStreaks();
                    restartUserJoinStreakRewards(user);
                    playersReset++;

                }
            }
        }

        return playersReset;
    }

    public void restartUserJoinStreakRewards(DBUser user) {
        Set<String> userRewards = user.getReceivedRewards();
        for (String rewardId : userRewards) {
            user.unreceiveReward(rewardId);
        }
        user.migrateUnclaimedRewards();
        user.resetRelativeJoinStreak();
    }
}