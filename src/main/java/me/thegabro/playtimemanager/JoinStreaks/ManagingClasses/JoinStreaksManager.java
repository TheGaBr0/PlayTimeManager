package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class JoinStreaksManager {
    private PlayTimeManager plugin;
    private static PlayTimeDatabase db;
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

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

    public void initialize(PlayTimeManager playTimeManager) {
        this.plugin = playTimeManager;
        db = plugin.getDatabase();

        this.rewardRegistry = new RewardRegistry(plugin);
        this.cycleScheduler = new CycleScheduler(plugin);
        this.streakTracker = new StreakTracker(plugin, dbUsersManager);
        this.rewardExecutor = new RewardExecutor(plugin);
        this.messageService = new RewardMessageService(plugin);
        this.rewardProcessor = new RewardProcessor(
                plugin,
                rewardRegistry,
                streakTracker,
                rewardExecutor,
                messageService
        );

        rewardRegistry.createRewardsDirectory();
        rewardRegistry.loadRewards();

        cycleScheduler.initialize();

        if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
            cycleScheduler.startIntervalTask();
        }
    }

    public void processPlayerLogin(Player player) {
        OnlineUser user = onlineUsersManager.getOnlineUser(player.getName());

        if (cycleScheduler.isEligibleForStreak(user)) {
            // Always increment absolute streak
            streakTracker.incrementAbsoluteStreak(user);
            cycleScheduler.markPlayerJoinedInCurrentCycle(user.getUuid());

            // Only increment relative streak and check rewards if schedule is active AND rewards exist
            if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewardRegistry.isEmpty()) {
                streakTracker.incrementRelativeStreak(user);
                rewardProcessor.processEligibleRewards(user, player);
            }
        }
    }

    public void resetMissingPlayerStreaks() {
        if (plugin.getConfiguration().getJoinStreakResetActivation()) {
            Set<String> playersWithStreaks = db.getPlayersWithActiveStreaks();
            int playersReset = streakTracker.resetInactivePlayerStreaks(
                    playersWithStreaks,
                    cycleScheduler.getIntervalSeconds(),
                    plugin.getConfiguration().getJoinStreakResetMissesAllowed()
            );

            if (plugin.getConfiguration().getStreakCheckVerbose()) {
                plugin.getLogger().info(String.format("Streak reset for %d players", playersReset));
            }
        }

        // Process online players
        onlineUsersManager.getOnlineUsersByUUID().values().forEach(this::processOnlineUserForCycleReset);
    }

    private void processOnlineUserForCycleReset(OnlineUser onlineUser) {
        // Always increment absolute streak
        streakTracker.incrementAbsoluteStreak(onlineUser);
        cycleScheduler.markPlayerJoinedInCurrentCycle(onlineUser.getUuid());

        // Only increment relative streak and check rewards if schedule is active AND rewards exist
        if (plugin.getConfiguration().getRewardsCheckScheduleActivation() && !rewardRegistry.isEmpty()) {
            streakTracker.incrementRelativeStreak(onlineUser);
            Player player = onlineUser.getPlayer();
            if (player != null) {
                rewardProcessor.processEligibleRewards(onlineUser, player);
            }
        }
    }

    public boolean toggleJoinStreakCheckSchedule(CommandSender sender) {
        boolean currentState = plugin.getConfiguration().getRewardsCheckScheduleActivation();
        plugin.getConfiguration().setRewardsCheckScheduleActivation(!currentState);

        if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
            if (rewardRegistry.isEmpty()) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " No active rewards found. Join streak check schedule not started."));
                plugin.getConfiguration().setRewardsCheckScheduleActivation(false);
                return false;
            }

            cycleScheduler.startIntervalTask();
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
