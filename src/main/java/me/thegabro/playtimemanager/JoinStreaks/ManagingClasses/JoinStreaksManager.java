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

public class JoinStreaksManager {
    private PlayTimeManager plugin = PlayTimeManager.getInstance();
    private DatabaseHandler db = DatabaseHandler.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private RewardRegistry rewardRegistry;
    private StreakTracker streakTracker;
    private CycleScheduler cycleScheduler;
    private RewardProcessor rewardProcessor;
    private RewardExecutor rewardExecutor;
    private RewardMessageService messageService;

    private JoinStreaksManager() {}

    private static final class InstanceHolder {
        private static JoinStreaksManager instance = new JoinStreaksManager();
    }

    public static JoinStreaksManager getInstance() {
        return InstanceHolder.instance;
    }

    public void initialize() {

        this.rewardRegistry = new RewardRegistry();
        this.cycleScheduler = new CycleScheduler();
        this.streakTracker = new StreakTracker();
        this.rewardExecutor = new RewardExecutor();
        this.messageService = new RewardMessageService();
        this.rewardProcessor = new RewardProcessor(
                rewardRegistry,
                streakTracker,
                rewardExecutor,
                messageService
        );

        rewardRegistry.createRewardsDirectory();
        rewardRegistry.loadRewards();

        cycleScheduler.initialize();

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation")) {
            cycleScheduler.getNextSchedule();
        }
    }

    public void processPlayerLogin(Player player) {
        OnlineUser user = onlineUsersManager.getOnlineUser(player.getName());

        if (cycleScheduler.isEligibleForStreak(user)) {
            // Always increment absolute streak
            streakTracker.incrementAbsoluteStreak(user);
            cycleScheduler.markPlayerJoinedInCurrentCycle(user.getUuid());

            // Only increment relative streak and check rewards if schedule is active AND rewards exist
            if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation") && !rewardRegistry.isEmpty()) {
                streakTracker.incrementRelativeStreak(user);
                rewardProcessor.processEligibleRewards(user, player);
            }
        }
    }

    public void resetMissingPlayerStreaksAsync() {
        if (!plugin.getConfiguration().getBoolean("reset-joinstreak.enabled")) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> playersWithStreaks = db.getStreakDAO().getPlayersWithActiveStreaks();

            streakTracker.resetInactivePlayerStreaksAsync(
                    playersWithStreaks,
                    cycleScheduler.getIntervalSeconds(),
                    plugin.getConfiguration().getInt("reset-joinstreak.missed-joins"),
                    playersReset -> {
                        if (plugin.getConfiguration().getBoolean("streak-check-verbose")) {
                            plugin.getLogger().info(String.format("Streak reset for %d players", playersReset));
                        }
                    }
            );
        });

        // Process online players on main thread if needed
        onlineUsersManager.getOnlineUsersByUUID().values().forEach(this::processOnlineUserForCycleReset);
    }

    private void processOnlineUserForCycleReset(OnlineUser onlineUser) {
        // Always increment absolute streak
        streakTracker.incrementAbsoluteStreak(onlineUser);
        cycleScheduler.markPlayerJoinedInCurrentCycle(onlineUser.getUuid());

        // Only increment relative streak and check rewards if schedule is active AND rewards exist
        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation") && !rewardRegistry.isEmpty()) {
            streakTracker.incrementRelativeStreak(onlineUser);
            Player player = onlineUser.getPlayerInstance();
            if (player != null) {
                rewardProcessor.processEligibleRewards(onlineUser, player);
            }
        }
    }

    public boolean toggleJoinStreakCheckSchedule(CommandSender sender) {
        boolean currentState = plugin.getConfiguration().getBoolean("rewards-check-schedule-activation");
        plugin.getConfiguration().set("rewards-check-schedule-activation", !currentState);

        if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation")) {
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

        // Clean up singleton reference to allow garbage collection
        InstanceHolder.instance = null;
    }

    // Getters for components
    public RewardRegistry getRewardRegistry() {
        return rewardRegistry;
    }

    public StreakTracker getStreakTracker() {
        return streakTracker;
    }

    public RewardExecutor getRewardExecutor(){
        return rewardExecutor;
    }

    public CycleScheduler getCycleScheduler() {
        return cycleScheduler;
    }

    public RewardProcessor getRewardProcessor() {
        return rewardProcessor;
    }
}
