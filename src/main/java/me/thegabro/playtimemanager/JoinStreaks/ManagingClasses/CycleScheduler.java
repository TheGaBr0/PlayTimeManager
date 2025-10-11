package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
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
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private CronExpression cronExpression;
    private TimeZone timezone;
    private Date nextIntervalReset;
    private long exactIntervalSeconds;
    private DatabaseHandler db = DatabaseHandler.getInstance();
    private BukkitTask intervalTask;
    private final Set<String> playersJoinedDuringCurrentCycle = new HashSet<>();
    private String checkTimeToText;

    public CycleScheduler() {}

    public void initialize() {
        validateConfiguration();

        Date now = new Date();
        Date firstTrigger = cronExpression.getNextValidTimeAfter(now);
        Date secondTrigger = cronExpression.getNextValidTimeAfter(firstTrigger);

        long intervalMillis = secondTrigger.getTime() - firstTrigger.getTime();
        exactIntervalSeconds = intervalMillis / 1000;
        translateCheckTimeToText();

        nextIntervalReset = null;

        scheduleNextReset();
    }

    private void validateConfiguration() {
        try {
            // Validate cron expression (using Quartz cron expression)
            String cronString = plugin.getConfiguration().getString("streak-reset-schedule");
            this.cronExpression = new CronExpression(cronString);

            // Set the timezone
            String timezoneConfig = plugin.getConfiguration().getString("reset-schedule-timezone");
            if ("utc".equalsIgnoreCase(timezoneConfig)) {
                this.timezone = TimeZone.getTimeZone("UTC");
            } else {
                this.timezone = TimeZone.getDefault();
            }
            this.cronExpression.setTimeZone(timezone);

            if (plugin.getConfiguration().getBoolean("streak-check-verbose")) {
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

    public void translateCheckTimeToText() {
        try {
            CronParser parser = new CronParser(
                    CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
            );

            var cron = parser.parse(this.cronExpression.toString());
            cron.validate();

            CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
            this.checkTimeToText = descriptor.describe(cron);

        } catch (Exception e) {
            this.checkTimeToText = "Invalid Quartz cron expression";
        }
    }

    private void scheduleNextReset() {
        cancelIntervalTask();

        Date oldSchedule;

        oldSchedule = Objects.requireNonNullElseGet(nextIntervalReset, Date::new);

        nextIntervalReset = cronExpression.getNextValidTimeAfter(oldSchedule);

        long delayInMillis = nextIntervalReset.getTime() - oldSchedule.getTime();

        long delayInTicks = Math.max(20, delayInMillis / 50); // Min 1 second (20 ticks)

        if (JoinStreaksManager.getInstance().getRewardRegistry().isEmpty() && plugin.getConfiguration().getBoolean("streak-check-verbose")) {
            plugin.getLogger().info("No active rewards found, but scheduler will continue running to track absolute join streaks.");
        }

        if (plugin.getConfiguration().getBoolean("streak-check-verbose")) {
            plugin.getLogger().info("Next join streak interval reset scheduled for: " + nextIntervalReset +
                    " (in " + Utils.ticksToFormattedPlaytime(delayInTicks) + ")");
        }

        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                playersJoinedDuringCurrentCycle.clear();
                JoinStreaksManager.getInstance().resetMissingPlayerStreaks();
                scheduleNextReset();
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
        }

        if (playersJoinedDuringCurrentCycle.contains(user.getUuid())) {
            return false;
        }

        long secondsBetween = Duration.between(user.getLastSeen(), LocalDateTime.now()).getSeconds();
        return secondsBetween <= exactIntervalSeconds * plugin.getConfiguration().getInt("reset-joinstreak.missed-joins");
    }

    public boolean isCurrentCycle() {
        // Get current cycle info based on cronExpression
        Date now = new Date();
        Date previousReset = cronExpression.getTimeAfter(new Date(now.getTime() - exactIntervalSeconds * 1000));

        // We're in a valid cycle if the current time is between the previous reset and the next reset
        return now.after(previousReset) && now.before(nextIntervalReset);
    }

    public Map<String, Object> getNextSchedule() {

        Map<String, Object> scheduleInfo = new HashMap<>();

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation")) {
            scheduleInfo.put("nextReset", nextIntervalReset);
            Date now = new Date();
            long delayInMillis = nextIntervalReset.getTime() - now.getTime();
            long delayInTicks = Math.max(20, delayInMillis / 50);

            scheduleInfo.put("timeRemaining", Utils.ticksToFormattedPlaytime(delayInTicks));
        } else {
            scheduleInfo.put("nextReset", null);
            scheduleInfo.put("timeRemaining", "-");
        }
        scheduleInfo.put("timeCheckToText", checkTimeToText);
        return scheduleInfo;
    }

    public void updateOnReload() {
        // Update interval reset times first to ensure accurate cycle information
        Date now = new Date();
        nextIntervalReset = cronExpression.getNextValidTimeAfter(now);

        // Recalculate if we're in the same cycle as when we last tracked
        if (!isCurrentCycle()) {
            playersJoinedDuringCurrentCycle.clear();
        }

        // If schedule is active, add players to the joined list whose last seen is within the current cycle
        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation")) {
            try {
                //TODO: async
                Set<String> playersWithStreaks = db.getStreakDAO().getPlayersWithActiveStreaks();
                Date cycleStartDate = new Date(nextIntervalReset.getTime() - exactIntervalSeconds * 1000);

                for (String playerUUID : playersWithStreaks) {
                    DBUser user = DBUsersManager.getInstance().getUserFromUUIDWithContext(playerUUID, "add players to current active cycle time window");
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
