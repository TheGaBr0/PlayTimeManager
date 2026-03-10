package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

/**
 * Central coordinator for the join streak system.
 *
 * Delegates cycle timing to {@link CycleScheduler}, streak tracking to {@link StreakTracker},
 * reward evaluation to {@link RewardProcessor}, and reward delivery to {@link RewardExecutor}.
 * Entry points are player login events and the scheduled cycle reset.
 */
public class JoinStreaksManager {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler db = DatabaseHandler.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private final RewardRegistry rewardRegistry = RewardRegistry.getInstance();
    private final CycleScheduler cycleScheduler = CycleScheduler.getInstance();
    private final StreakTracker streakTracker = StreakTracker.getInstance();
    private final RewardExecutor rewardExecutor = RewardExecutor.getInstance();
    private final RewardMessageService messageService = RewardMessageService.getInstance();
    private RewardProcessor rewardProcessor;

    private JoinStreaksManager() {}

    private static final class InstanceHolder {
        private static JoinStreaksManager instance = new JoinStreaksManager();
    }

    public static JoinStreaksManager getInstance() {
        return InstanceHolder.instance;
    }

    /** Loads rewards, wires up the processor, and starts the cycle scheduler. */
    public void initialize() {

        rewardProcessor = RewardProcessor.initialize(
                rewardRegistry,
                streakTracker,
                rewardExecutor,
                messageService
        );

        rewardRegistry.createRewardsDirectory();
        rewardRegistry.loadRewards();

        cycleScheduler.initialize();

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true)) {
            cycleScheduler.getNextSchedule();
        }
    }

    /**
     * Handles all streak logic when a player joins the server.
     *
     * If the player already joined this cycle, nothing happens. Otherwise, eligibility
     * is checked, if they missed too many cycles their streaks are reset, then the
     * absolute streak is incremented and, if the schedule is active, the relative streak
     * and any matching rewards are processed.
     */
    public void processPlayerLogin(OnlineUser onlineUser) {

        if (cycleScheduler.playersJoinedDuringCurrentCycle.contains(onlineUser.getUuid())) {
            return;
        }

        boolean isEligible = cycleScheduler.isEligibleForStreak(onlineUser);

        if (!isEligible)
            onlineUser.resetJoinStreaks();

        streakTracker.incrementAbsoluteStreak(onlineUser);
        cycleScheduler.markPlayerJoinedInCurrentCycle(onlineUser.getUuid());

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true) && !rewardRegistry.isEmpty()) {
            streakTracker.incrementRelativeStreak(onlineUser);
            rewardProcessor.processEligibleRewards(onlineUser);
        }
    }

    /**
     * Called at the end of each cycle to reset streaks for players who didn't join.
     * Offline players are handled asynchronously via a database lookup;
     * online players are processed directly on the main thread.
     */
    public void resetMissingPlayerStreaksAsync() {
        if (!plugin.getConfiguration().getBoolean("reset-joinstreak.enabled", true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> playersWithStreaks = db.getStreakDAO().getPlayersWithActiveStreaks();

            streakTracker.resetInactivePlayerStreaksAsync(
                    playersWithStreaks,
                    cycleScheduler.getIntervalSeconds(),
                    plugin.getConfiguration().getInt("reset-joinstreak.missed-joins", 1),
                    playersReset -> {
                        if (plugin.getConfiguration().getBoolean("streak-check-verbose", false)) {
                            plugin.getLogger().info(String.format("Streak reset for %d players", playersReset));
                        }
                    }
            );
        });

        onlineUsersManager.getOnlineUsersByUUID().values().forEach(this::processOnlineUserForCycleReset);
    }

    /**
     * Applies cycle-reset streak logic to a player who is currently online.
     * Their absolute streak is incremented and, if applicable, their relative streak
     * and rewards are processed, equivalent to a fresh login event for the new cycle.
     */
    private void processOnlineUserForCycleReset(OnlineUser onlineUser) {
        streakTracker.incrementAbsoluteStreak(onlineUser);
        cycleScheduler.markPlayerJoinedInCurrentCycle(onlineUser.getUuid());

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true) && !rewardRegistry.isEmpty()) {
            streakTracker.incrementRelativeStreak(onlineUser);
            Player player = onlineUser.getPlayerInstance();

            if (player != null) {
                rewardProcessor.processEligibleRewards(onlineUser);
            }
        }
    }

    /**
     * Toggles the join streak check schedule on or off.
     * If turning on, verifies that at least one reward exists before activating.
     * Returns true if the state change was applied successfully.
     */
    public boolean toggleJoinStreakCheckSchedule(CommandSender sender) {
        boolean currentState = plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true);
        plugin.getConfiguration().set("rewards-check-schedule-activation", !currentState);

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true)) {
            if (rewardRegistry.isEmpty()) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        " No active rewards found. Join streak check schedule not started."));
                plugin.getConfiguration().set("rewards-check-schedule-activation", false);
                return false;
            }

            cycleScheduler.getNextSchedule();
            messageService.sendScheduleActivationMessage(sender, true);

            Map<String, Object> scheduleInfo = cycleScheduler.getNextSchedule();
            messageService.sendNextResetMessage(sender, scheduleInfo);
        } else {
            cycleScheduler.cancelIntervalTask();
            messageService.sendScheduleActivationMessage(sender, false);
            plugin.getLogger().info("The join streak check schedule has been deactivated from GUI button");
        }
        return true;
    }

    public Map<String, Object> getNextSchedule() {
        return cycleScheduler.getNextSchedule();
    }

    public void onServerReload() {
        cycleScheduler.updateOnReload();
    }

    public void cleanUp() {
        cycleScheduler.cleanUp();
        rewardRegistry.cleanUp();

        InstanceHolder.instance = null;
    }
}