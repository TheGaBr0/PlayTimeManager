package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class CycleScheduler {
    private final PlayTimeManager plugin;
    private CronExpression cronExpression;
    private TimeZone timezone;
    private Date nextIntervalReset;
    private long exactIntervalSeconds;
    private long currentCycleStartTime;
    private BukkitTask intervalTask;
    private final Set<String> playersJoinedDuringCurrentCycle = new HashSet<>();

    public CycleScheduler(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.currentCycleStartTime = System.currentTimeMillis();
    }

    public void initialize() {
        validateConfiguration();

        Date now = new Date();
        Date firstTrigger = cronExpression.getNextValidTimeAfter(now);
        Date secondTrigger = cronExpression.getNextValidTimeAfter(firstTrigger);

        long intervalMillis = secondTrigger.getTime() - firstTrigger.getTime();
        exactIntervalSeconds = intervalMillis / 1000;

        updateIntervalResetTimes();
    }

    private void validateConfiguration() {
        try {
            // Validate cron expression (using Quartz cron expression)
            String cronString = plugin.getConfiguration().getStreakResetSchedule();
            this.cronExpression = new CronExpression(cronString);

            // Set the timezone
            String timezoneConfig = plugin.getConfiguration().getStreakTimeZone();
            if ("utc".equalsIgnoreCase(timezoneConfig)) {
                this.timezone = TimeZone.getTimeZone("UTC");
            } else {
                this.timezone = TimeZone.getDefault();
            }
            this.cronExpression.setTimeZone(timezone);

            if (plugin.getConfiguration().getStreakCheckVerbose()) {
                plugin.getLogger().info("Join streak configuration validated successfully");
                plugin.getLogger().info("Using cron schedule: " + cronString);
                plugin.getLogger().info("Using timezone: " + timezone.getID());
            }

        } catch (ParseException e) {
            plugin.getLogger().severe("Invalid cron expression in config! Using default: 0 0 0 * * ?");
            try {
                // Make sure the default is in the correct format for Quartz CronExpression
                this.cronExpression = new CronExpression("0 0 0 * * ?");
                this.timezone = TimeZone.getDefault();
                this.cronExpression.setTimeZone(timezone);
            } catch (ParseException ex) {
                plugin.getLogger().severe("Critical error initializing cron scheduler: " + ex.getMessage());
            }
        }
    }

    public void updateIntervalResetTimes() {
        Date now = new Date();
        nextIntervalReset = cronExpression.getNextValidTimeAfter(now);
    }

    public void startIntervalTask() {
        updateIntervalResetTimes();
        scheduleNextReset();
    }

    private void scheduleNextReset() {
        cancelIntervalTask();

        if (JoinStreaksManager.getInstance().getRewardRegistry().isEmpty() && plugin.getConfiguration().getStreakCheckVerbose()) {
            plugin.getLogger().info("No active rewards found, but scheduler will continue running to track absolute join streaks.");
        }

        Date now = new Date();
        // Add a small buffer (e.g., 1 second) to avoid scheduling for a time that's too close
        if (nextIntervalReset.getTime() - now.getTime() < 1000) {
            // Get the next interval after the current nextIntervalReset
            nextIntervalReset = cronExpression.getNextValidTimeAfter(nextIntervalReset);
        }

        long delayInMillis = nextIntervalReset.getTime() - now.getTime();
        long delayInTicks = Math.max(20, delayInMillis / 50); // Min 1 second (20 ticks)

        if (plugin.getConfiguration().getStreakCheckVerbose()) {
            plugin.getLogger().info("Next join streak interval reset scheduled for: " + nextIntervalReset +
                    " (in " + Utils.ticksToFormattedPlaytime(delayInTicks) + ")");
        }

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                playersJoinedDuringCurrentCycle.clear();
                currentCycleStartTime = System.currentTimeMillis();

                JoinStreaksManager.getInstance().resetMissingPlayerStreaks();

                // Calculate the next reset time and reschedule
                Date oldNextReset = nextIntervalReset;
                updateIntervalResetTimes();

                // Ensure we don't get stuck in a loop if times are too close
                if (Math.abs(nextIntervalReset.getTime() - oldNextReset.getTime()) < 1000) {
                    nextIntervalReset = cronExpression.getNextValidTimeAfter(nextIntervalReset);
                }

                scheduleNextReset(); // Re-run with the updated time
            }
        }.runTaskLater(plugin, delayInTicks);
    }

    public void cancelIntervalTask() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
    }

    public boolean isEligibleForStreak(OnlineUser user) {
        if (!isCurrentCycle()) {
            playersJoinedDuringCurrentCycle.clear();
            currentCycleStartTime = System.currentTimeMillis();
        }

        if (playersJoinedDuringCurrentCycle.contains(user.getUuid())) {
            return false;
        }

        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();
        return secondsBetween <= exactIntervalSeconds * plugin.getConfiguration().getJoinStreakResetMissesAllowed();
    }

    public boolean isCurrentCycle() {
        // Get current cycle info based on cronExpression
        Date now = new Date();
        Date previousReset = cronExpression.getTimeAfter(new Date(now.getTime() - exactIntervalSeconds * 1000));

        // We're in a valid cycle if the current time is between the previous reset and the next reset
        return now.after(previousReset) && now.before(nextIntervalReset);
    }

    public Map<String, Object> getNextSchedule() {
        updateIntervalResetTimes();

        Map<String, Object> scheduleInfo = new HashMap<>();

        if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
            scheduleInfo.put("nextReset", nextIntervalReset);
            Date now = new Date();
            long delayInMillis = nextIntervalReset.getTime() - now.getTime();
            long delayInTicks = Math.max(20, delayInMillis / 50);

            scheduleInfo.put("timeRemaining", Utils.ticksToFormattedPlaytime(delayInTicks));
        } else {
            scheduleInfo.put("nextReset", null);
            scheduleInfo.put("timeRemaining", "-");
        }

        return scheduleInfo;
    }

    public void updateOnReload() {
        // Update interval reset times first to ensure accurate cycle information
        updateIntervalResetTimes();

        // Recalculate if we're in the same cycle as when we last tracked
        if (!isCurrentCycle()) {
            playersJoinedDuringCurrentCycle.clear();
            currentCycleStartTime = System.currentTimeMillis();
        }

        // If schedule is active, add players to the joined list whose last seen is within the current cycle
        if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
            try {
                Set<String> playersWithStreaks = plugin.getDatabase().getPlayersWithActiveStreaks();
                Date cycleStartDate = new Date(nextIntervalReset.getTime() - exactIntervalSeconds * 1000);

                for (String playerUUID : playersWithStreaks) {
                    DBUser user = DBUsersManager.getInstance().getUserFromUUID(playerUUID);
                    if (user != null) {
                        LocalDateTime lastSeen = user.getLastSeen();

                        if (lastSeen == null) {
                            continue;
                        }

                        Date lastSeenDate = Date.from(lastSeen.atZone(ZoneId.systemDefault()).toInstant());

                        // Check if the player's last seen time is within the current cycle
                        if (lastSeenDate.after(cycleStartDate) && lastSeenDate.before(nextIntervalReset)) {
                            playersJoinedDuringCurrentCycle.add(playerUUID);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing players during reload: " + e.getMessage());
            }
        }
    }

    public void markPlayerJoinedInCurrentCycle(String uuid) {
        playersJoinedDuringCurrentCycle.add(uuid);
    }

    public long getIntervalSeconds() {
        return exactIntervalSeconds;
    }

    public void cleanUp() {
        cancelIntervalTask();
        playersJoinedDuringCurrentCycle.clear();
    }


}
