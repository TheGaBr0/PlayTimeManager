package me.thegabro.playtimemanager.Users;

import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineUsersManager {
    private static volatile OnlineUsersManager instance;
    private BukkitTask goalSchedule;
    private BukkitTask dbUpdateSchedule;
    private final PlayTimeManager plugin;
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final Map<String, OnlineUser> onlineUsersByName;
    private final Map<String, OnlineUser> onlineUsersByUUID;
    private final Map<String, String> goalMessageReplacements;

    private static final int DB_UPDATE_INTERVAL = 300 * 20; // 5 minutes in ticks

    private OnlineUsersManager() {
        this.plugin = PlayTimeManager.getInstance();
        this.onlineUsersByName = new ConcurrentHashMap<>();
        this.onlineUsersByUUID = new ConcurrentHashMap<>();
        this.goalMessageReplacements = new HashMap<>();
        loadOnlineUsers();
    }

    public void initialize(){
        startGoalCheckSchedule();
        startDBUpdateSchedule();
    }

    public static OnlineUsersManager getInstance() {
        if (instance == null) {
            synchronized (OnlineUsersManager.class) {
                if (instance == null) {
                    instance = new OnlineUsersManager();
                }
            }
        }
        return instance;
    }

    public Map<String, OnlineUser> getOnlineUsersByUUID(){
        return onlineUsersByUUID;
    }

    public void addOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.put(onlineUser.getNickname().toLowerCase(), onlineUser);
        onlineUsersByUUID.put(onlineUser.getUuid(), onlineUser);
    }

    public void removeOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.remove(onlineUser.getNickname().toLowerCase());
        onlineUsersByUUID.remove(onlineUser.getUuid());
    }

    public void loadOnlineUsers() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            OnlineUser onlineUser = new OnlineUser(player);
            addOnlineUser(onlineUser);
        });
    }

    public OnlineUser getOnlineUser(String nickname) {
        return onlineUsersByName.get(nickname.toLowerCase());
    }

    public OnlineUser getOnlineUserByUUID(String uuid) {
        return onlineUsersByUUID.get(uuid);
    }

    public void startGoalCheckSchedule() {
        if (goalSchedule != null) {
            goalSchedule.cancel();
        }

        Set<Goal> goals = goalsManager.getGoals();
        if (goals.isEmpty() || goalsManager.areAllInactive()) {
            plugin.getLogger().info("No active goals found. Goal check schedule not started.");
            return;
        }

        goalSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                checkGoalsForAllUsers(goals);
            }
        }.runTaskTimer(plugin, 0, plugin.getConfiguration().getInt("goal-check-rate") * 20);
    }

    private void checkGoalsForAllUsers(Set<Goal> goals) {
        if (plugin.getConfiguration().getBoolean("goal-check-verbose")) {
            logVerboseInfo();
        }

        onlineUsersByName.values().forEach(onlineUser -> {
            Player player = Bukkit.getPlayerExact(onlineUser.getNickname());
            if (player != null) {
                checkGoalsForUser(onlineUser, player, goals);
            }
        });
    }

    private void checkGoalsForUser(OnlineUser onlineUser, Player player, Set<Goal> goals) {
        goals.stream()
                .filter(Goal::isActive)
                .filter(goal -> !onlineUser.hasCompletedGoal(goal.getName()))
                .filter(goal -> goal.getRequirements().checkRequirements(player, onlineUser.getPlaytime()))
                .forEach(goal -> processCompletedGoal(onlineUser, player, goal));
    }

    private void processCompletedGoal(OnlineUser onlineUser, Player player, Goal goal) {
        onlineUser.markGoalAsCompleted(goal.getName());

        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForGoal(onlineUser, goal);
        }

        executeCommands(goal, player);
        sendGoalMessage(player, goal);

        if(plugin.getConfiguration().getBoolean("goal-check-verbose")){
            plugin.getLogger().info(String.format("User %s has reached the goal %s which requires %s!",
                    onlineUser.getNickname(), goal.getName(),Utils.ticksToFormattedPlaytime(goal.getRequirements().getTime())));
        }


        playGoalSound(player, goal);
    }

    private void startDBUpdateSchedule() {
        if (dbUpdateSchedule != null) {
            dbUpdateSchedule.cancel();
        }

        dbUpdateSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllOnlineUsersPlaytime();
            }
        }.runTaskTimer(plugin, 0, DB_UPDATE_INTERVAL);
    }

    public CompletableFuture<Void> updateAllOnlineUsersPlaytime() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                onlineUsersByName.values().forEach(user -> {
                    try {
                        user.updatePlayTime();
                        user.updateLastSeen();
                    } catch (Exception e) {
                        plugin.getLogger().severe(String.format("Failed to update playtime for user %s: %s",
                                user.getNickname(), e.getMessage()));
                    }
                });
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void assignPermissionsForGoal(OnlineUser onlineUser, Goal goal) {
        List<String> permissions = goal.getRewardPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            try {
                LuckPermsManager.getInstance(plugin).assignGoalPermissions(onlineUser.getUuid(), goal);
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("Failed to assign permissions for goal %s to player %s: %s",
                        goal.getName(), onlineUser.getNickname(), e.getMessage()));
            }
        }
    }

    private void executeCommands(Goal goal, Player player) {
        List<String> commands = goal.getRewardCommands();
        if (commands != null && !commands.isEmpty()) {
            commands.forEach(command -> {
                try {
                    String formattedCommand = formatCommand(command, player);
                    if (plugin.getConfiguration().getBoolean("goal-check-verbose")) {
                        plugin.getLogger().info("Executing command: " + formattedCommand);
                    }
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe(String.format("Failed to execute command for goal %s: %s",
                            goal.getName(), e.getMessage()));
                }
            });
        }
    }

    private String formatCommand(String command, Player player) {
        goalMessageReplacements.put("PLAYER_NAME", player.getName());
        return replacePlaceholders(command).replaceFirst("/", "");
    }

    private void playGoalSound(Player player, Goal goal) {
        try {
            String soundName = goal.getGoalSound();
            Sound sound = null;

            // Simple direct field access - most efficient when the name matches exactly
            try {
                sound = (Sound) Sound.class.getField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Log the actual error for debugging if verbose is enabled
                if (plugin.getConfiguration().getBoolean("goal-check-verbose")) {
                    plugin.getLogger().info("Could not find sound directly, attempting fallback: " + e.getMessage());
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning(String.format("Could not find sound '%s' for goal '%s'",
                        soundName, goal.getName()));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for goal '%s': %s",
                    goal.getGoalSound(), goal.getName(), e.getMessage()));
        }
    }

    private void sendGoalMessage(Player player, Goal goal) {
        goalMessageReplacements.put("%PLAYER_NAME%", player.getName());
        goalMessageReplacements.put("%TIME_REQUIRED%",
                goal.getRequirements().getTime() != Long.MAX_VALUE ? Utils.ticksToFormattedPlaytime(goal.getRequirements().getTime()) : "-");
        goalMessageReplacements.put("%GOAL_NAME%", goal.getName());
        player.sendMessage(Utils.parseColors(replacePlaceholders(goal.getGoalMessage())));
    }


    private String replacePlaceholders(String input) {
        String result = input;
        for (Map.Entry<String, String> entry : goalMessageReplacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        goalMessageReplacements.clear();
        return result;
    }

    private void logVerboseInfo() {
        plugin.getLogger().info(String.format("Goal check schedule started, refresh rate is %ss", plugin.getConfiguration().getInt("goal-check-rate")));
    }

    public void stopSchedules() {
        Optional.ofNullable(goalSchedule).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(dbUpdateSchedule).ifPresent(BukkitTask::cancel);
    }
}
