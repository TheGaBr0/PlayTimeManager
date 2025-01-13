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
    private BukkitTask schedule;
    private String configSound, configMessage;
    protected final PlayTimeManager plugin = PlayTimeManager.getInstance();
    protected final ArrayList<OnlineUser> onlineUsers = new ArrayList<>();
    private final PlayTimeDatabase db = plugin.getDatabase();
    private Map<String, String> goalMessageReplacements = new HashMap<>();


    public OnlineUsersManager() {

        loadOnlineUsers();
        restartSchedule();
    }

    public boolean userExists(String nickname) {
        return db.getUUIDFromNickname(nickname) != null;
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

    public void restartSchedule() {
        if (schedule != null) {
            schedule.cancel();
        }

        schedule = new BukkitRunnable() {
            public void run() {

                Player p;
                PlayTimeDatabase db = plugin.getDatabase();
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

                                // Mark goal as completed
                                onlineUser.markGoalAsCompleted(goal.getName());

                                if (plugin.isPermissionsManagerConfigured()) {
                                    assignPermissionsForGoal(onlineUser, goal);
                                }

                                executeCommands(goal, p);

                                // Play sound
                                try {
                                    configSound = goal.getGoalSound();
                                    Sound sound = Sound.valueOf(configSound);
                                    p.playSound(p.getLocation(), sound, 10, 0);
                                } catch (IllegalArgumentException exception) {
                                    plugin.getLogger().severe(configSound + " is not a valid argument for goal-sound" +
                                            "setting in " + goal.getName() + ".yml");
                                }

                                // Send message
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
        // Get the list of commands associated with the goal
        List<String> commands = goal.getCommands();
        String formattedCommand;
        if (commands != null && !commands.isEmpty()) {
            for (String command : commands) {
                // Format the command to execute as the server console
                goalMessageReplacements.put("PLAYER_NAME", player.getName());
                formattedCommand = replacePlaceholders(command);
                formattedCommand = formattedCommand.replaceFirst("/", "");
                try {
                    // Execute each command as the console
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
