package Users;

import ExternalPluginSupport.LuckPermsManager;
import Goals.Goal;
import Goals.GoalsManager;
import SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class OnlineUsersManager {
    private static OnlineUsersManager instance;
    private BukkitTask schedule;
    private BukkitTask dbUpdateSchedule;
    private String configSound, configMessage;
    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final ArrayList<OnlineUser> onlineUsers = new ArrayList<>();
    private final Map<String, String> goalMessageReplacements = new HashMap<>();

    private OnlineUsersManager() {
        loadOnlineUsers();
        startGoalCheckSchedule();
        startDBUpdateSchedule();
    }

    public static OnlineUsersManager getInstance() {
        if (instance == null) {
            instance = new OnlineUsersManager();
        }
        return instance;
    }

    public void addOnlineUser(OnlineUser onlineUser) {
        onlineUsers.add(onlineUser);
    }

    public void removeOnlineUser(OnlineUser onlineUser) {
        onlineUsers.remove(onlineUser);
    }

    private void loadOnlineUsers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            OnlineUser onlineUser = new OnlineUser(p);
            onlineUsers.add(onlineUser);
        }
    }

    public OnlineUser getOnlineUser(String nickname) {
        for (OnlineUser user : onlineUsers) {
            if (user.getNickname().equals(nickname))
                return user;
        }
        return null;
    }

    public OnlineUser getOnlineUserByUUID(String uuid) {
        for (OnlineUser user : onlineUsers) {
            if (user.getUuid().equals(uuid))
                return user;
        }
        return null;
    }

    public void startGoalCheckSchedule() {
        if (schedule != null) {
            schedule.cancel();
        }

        schedule = new BukkitRunnable() {
            @Override
            public void run() {
                Player p;
                if (plugin.getConfiguration().getGoalsCheckVerbose()) {
                    plugin.getLogger().info("Goal check schedule started, refresh rate is " +
                            convertTime(plugin.getConfiguration().getGoalsCheckRate()) +
                            ". If you find this message annoying you can deactivate it by changing goal-check-verbose in the config.yml");
                }

                Set<Goal> goals = GoalsManager.getGoals();

                if (goals.isEmpty()) {
                    schedule.cancel();
                    plugin.getLogger().info("No goal has been detected, goal check schedule canceled.");
                }

                if (GoalsManager.areAllInactive()) {
                    schedule.cancel();
                    plugin.getLogger().info("There's no active goal, goal check schedule canceled.");
                }

                for (OnlineUser onlineUser : onlineUsers) {
                    for (Goal goal : GoalsManager.getGoals()) {
                        if (!goal.isActive())
                            continue;

                        p = Bukkit.getPlayerExact(onlineUser.getNickname());

                        if (p != null) {
                            if (!onlineUser.hasCompletedGoal(goal.getName())
                                    && onlineUser.getPlaytime() >= goal.getTime()) {

                                onlineUser.markGoalAsCompleted(goal.getName());

                                if (plugin.isPermissionsManagerConfigured()) {
                                    assignPermissionsForGoal(onlineUser, goal);
                                }

                                executeCommands(goal, p);

                                try {
                                    configSound = goal.getGoalSound();
                                    Sound sound = Sound.valueOf(configSound);
                                    p.playSound(p.getLocation(), sound, 10, 0);
                                } catch (IllegalArgumentException exception) {
                                    plugin.getLogger().severe(configSound + " is not a valid argument for goal-sound" +
                                            "setting in " + goal.getName() + ".yml");
                                }

                                goalMessageReplacements.put("%PLAYER_NAME%", p.getName());
                                goalMessageReplacements.put("%TIME_REQUIRED%", convertTime(goal.getTime() / 20));
                                configMessage = replacePlaceholders(goal.getGoalMessage());

                                p.sendMessage(configMessage);

                                plugin.getLogger().info("User " + onlineUser.getNickname() + " has reached " +
                                        convertTime(goal.getTime() / 20) + "!");
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, plugin.getConfiguration().getGoalsCheckRate() * 20);
    }

    private void startDBUpdateSchedule() {
        if (dbUpdateSchedule != null) {
            dbUpdateSchedule.cancel();
        }

        dbUpdateSchedule = new BukkitRunnable() {
            @Override
            public void run() {
                for (OnlineUser user : onlineUsers) {
                    try {
                        user.updateDB();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to update playtime for online user " +
                                user.getNickname() + ": " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, (long) 300 * 20, (long) 300 * 20);  //each 5 minutes
    }

    private void assignPermissionsForGoal(OnlineUser onlineUser, Goal goal) {
        ArrayList<String> permissions = goal.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            try {
                LuckPermsManager.getInstance(plugin).assignGoalPermissions(onlineUser.getUuid(), goal);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to assign permissions for goal " +
                        goal.getName() + " to player " + onlineUser.getNickname() + ": " + e.getMessage());
            }
        }
    }

    private void executeCommands(Goal goal, Player player) {
        List<String> commands = goal.getCommands();
        String formattedCommand;
        if (commands != null && !commands.isEmpty()) {
            for (String command : commands) {
                goalMessageReplacements.put("PLAYER_NAME", player.getName());
                formattedCommand = replacePlaceholders(command);
                formattedCommand = formattedCommand.replaceFirst("/", "");
                try {
                    if (plugin.getConfiguration().getGoalsCheckVerbose())
                        plugin.getLogger().info("Executing command: " + formattedCommand);
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to execute command: " + formattedCommand + " for goal " + goal.getName());
                }
            }
        }
    }

    private String convertTime(long secondsx) {
        int days = (int) TimeUnit.SECONDS.toDays(secondsx);
        int hours = (int) (TimeUnit.SECONDS.toHours(secondsx) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(secondsx) - TimeUnit.HOURS.toMinutes(hours)
                - TimeUnit.DAYS.toMinutes(days));
        int seconds = (int) (TimeUnit.SECONDS.toSeconds(secondsx) - TimeUnit.MINUTES.toSeconds(minutes)
                - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days != 0) {
            return days + "d, " + hours + "h, " + minutes + "m, " + seconds + "s";
        } else {
            if (hours != 0) {
                return hours + "h, " + minutes + "m, " + seconds + "s";
            } else {
                if (minutes != 0) {
                    return minutes + "m, " + seconds + "s";
                } else {
                    return seconds + "s";
                }
            }
        }
    }

    private String replacePlaceholders(String input) {
        for (Map.Entry<String, String> entry : goalMessageReplacements.entrySet()) {
            input = input.replace(entry.getKey(), entry.getValue());
        }
        goalMessageReplacements.clear();
        return input;
    }
}