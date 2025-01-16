package Users;

import ExternalPluginSupport.LuckPermsManager;
import Goals.Goal;
import Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OnlineUsersManager {
    private static volatile OnlineUsersManager instance;
    private BukkitTask goalSchedule;
    private BukkitTask dbUpdateSchedule;
    private final PlayTimeManager plugin;
    private final Map<String, OnlineUser> onlineUsersByName;
    private final Map<String, OnlineUser> onlineUsersByUUID;
    private final Map<String, String> goalMessageReplacements;

    private static final int DB_UPDATE_INTERVAL = 300 * 20; // 5 minutes in ticks
    private static final String TIME_FORMAT_DAYS = "%dd, %dh, %dm, %ds";
    private static final String TIME_FORMAT_HOURS = "%dh, %dm, %ds";
    private static final String TIME_FORMAT_MINUTES = "%dm, %ds";
    private static final String TIME_FORMAT_SECONDS = "%ds";

    private OnlineUsersManager() {
        this.plugin = PlayTimeManager.getInstance();
        this.onlineUsersByName = new ConcurrentHashMap<>();
        this.onlineUsersByUUID = new ConcurrentHashMap<>();
        this.goalMessageReplacements = new HashMap<>();
        loadOnlineUsers();
        startGoalCheckSchedule();
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

    public void addOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.put(onlineUser.getNickname().toLowerCase(), onlineUser);
        onlineUsersByUUID.put(onlineUser.getUuid(), onlineUser);
    }

    public void removeOnlineUser(OnlineUser onlineUser) {
        onlineUsersByName.remove(onlineUser.getNickname().toLowerCase());
        onlineUsersByUUID.remove(onlineUser.getUuid());
    }

    private void loadOnlineUsers() {
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

        Set<Goal> goals = GoalsManager.getGoals();
        if (goals.isEmpty() || GoalsManager.areAllInactive()) {
            plugin.getLogger().info("No active goals found. Goal check schedule not started.");
            return;
        }

        goalSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                checkGoalsForAllUsers(goals);
            }
        }.runTaskTimer(plugin, 0, plugin.getConfiguration().getGoalsCheckRate() * 20);
    }

    private void checkGoalsForAllUsers(Set<Goal> goals) {
        if (plugin.getConfiguration().getGoalsCheckVerbose()) {
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
                .filter(goal -> onlineUser.getPlaytime() >= goal.getTime())
                .forEach(goal -> processCompletedGoal(onlineUser, player, goal));
    }

    private void processCompletedGoal(OnlineUser onlineUser, Player player, Goal goal) {
        onlineUser.markGoalAsCompleted(goal.getName());

        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForGoal(onlineUser, goal);
        }

        executeCommands(goal, player);
        playGoalSound(player, goal);
        sendGoalMessage(player, goal);

        plugin.getLogger().info(String.format("User %s has reached %s!",
                onlineUser.getNickname(), convertTime(goal.getTime() / 20)));
    }

    public void startDBUpdateSchedule() {
        if (dbUpdateSchedule != null) {
            dbUpdateSchedule.cancel();
        }

        dbUpdateSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllUsersDB();
            }
        }.runTaskTimer(plugin, 0, DB_UPDATE_INTERVAL);
    }

    private void updateAllUsersDB() {
        onlineUsersByName.values().forEach(user -> {
            try {
                user.updateDB();
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("Failed to update playtime for user %s: %s",
                        user.getNickname(), e.getMessage()));
            }
        });
    }

    private void assignPermissionsForGoal(OnlineUser onlineUser, Goal goal) {
        List<String> permissions = goal.getPermissions();
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
        List<String> commands = goal.getCommands();
        if (commands != null && !commands.isEmpty()) {
            commands.forEach(command -> {
                try {
                    String formattedCommand = formatCommand(command, player);
                    if (plugin.getConfiguration().getGoalsCheckVerbose()) {
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
            Sound sound = Sound.valueOf(goal.getGoalSound());
            player.playSound(player.getLocation(), sound, 10, 0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe(String.format("%s is not a valid sound in %s.yml",
                    goal.getGoalSound(), goal.getName()));
        }
    }

    private void sendGoalMessage(Player player, Goal goal) {
        goalMessageReplacements.put("%PLAYER_NAME%", player.getName());
        goalMessageReplacements.put("%TIME_REQUIRED%", convertTime(goal.getTime() / 20));
        player.sendMessage(replacePlaceholders(goal.getGoalMessage()));
    }

    private String convertTime(long seconds) {
        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        int hours = (int) (TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours) - TimeUnit.DAYS.toMinutes(days));
        int secs = (int) (TimeUnit.SECONDS.toSeconds(seconds) - TimeUnit.MINUTES.toSeconds(minutes) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days > 0) {
            return String.format(TIME_FORMAT_DAYS, days, hours, minutes, secs);
        } else if (hours > 0) {
            return String.format(TIME_FORMAT_HOURS, hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format(TIME_FORMAT_MINUTES, minutes, secs);
        } else {
            return String.format(TIME_FORMAT_SECONDS, secs);
        }
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
        plugin.getLogger().info(String.format("Goal check schedule started, refresh rate is %s",
                convertTime(plugin.getConfiguration().getGoalsCheckRate())));
    }

    public void stopSchedules() {
        Optional.ofNullable(goalSchedule).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(dbUpdateSchedule).ifPresent(BukkitTask::cancel);
    }
}
