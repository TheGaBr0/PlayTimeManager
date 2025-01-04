package Users;

import ExternalPluginSupport.LuckPermsManager;
import Goals.Goal;
import Goals.GoalManager;
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
    private final LuckPermsManager luckPermsManager;

    public OnlineUsersManager() {
        this.luckPermsManager = LuckPermsManager.getInstance(plugin);
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

    public void loadOnlineUsers() {
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
                if (!plugin.isPermissionsManagerConfigured())
                    return;

                Player p;
                PlayTimeDatabase db = plugin.getDatabase();
                if (plugin.getConfiguration().getGoalsCheckVerbose())
                    Bukkit.getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Goal check started, refresh rate is "
                            + convertTime(plugin.getConfiguration().getGoalsCheckRate()) +
                            ".\n If you find this message annoying you can deactivate it by changing §6goal-check-verbose " +
                            "§7in the config.yml");

                Set<Goal> goals = GoalManager.getGoals();

                if (goals.isEmpty()) {
                    schedule.cancel();
                    Bukkit.getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 No goal has been detected, " +
                            "goal check canceled.");
                }

                for (OnlineUser onlineUser : onlineUsers) {
                    for (Goal goal : GoalManager.getGoals()) {
                        if (!goal.isActive())
                            continue;

                        p = Bukkit.getPlayerExact(onlineUser.getNickname());

                        if (p != null) {
                            if (!db.hasCompletedGoal(p.getUniqueId().toString(), goal.getName())
                                    && onlineUser.getPlaytime() >= goal.getTime()) {

                                // Mark goal as completed
                                db.markGoalAsCompleted(p.getUniqueId().toString(), goal.getName());

                                // Assign permissions using LuckPermsManager
                                ArrayList<String> permissions = goal.getPermissions();
                                if (permissions != null && !permissions.isEmpty()) {
                                    try {
                                        luckPermsManager.assignGoalPermissions(onlineUser.getUuid(), goal);
                                    } catch (Exception e) {
                                        plugin.getLogger().severe("[§6PlayTime§eManager§f]§7 Failed to assign permissions for goal " +
                                                goal.getName() + " to player " + p.getName() + ": " + e.getMessage());
                                    }
                                }

                                // Play sound
                                try {
                                    configSound = goal.getGoalSound();
                                    Sound sound = Sound.valueOf(configSound);
                                    p.playSound(p.getLocation(), sound, 10, 0);
                                } catch (IllegalArgumentException exception) {
                                    plugin.getLogger().severe(configSound + " is not a valid argument for goal-sound" +
                                            "setting in " + goal.getName() + ".yaml");
                                }

                                // Send message
                                configMessage = replacePlaceholders(goal.getGoalMessage(), p, goal);
                                p.sendMessage(configMessage);

                                Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 User §e"
                                        + onlineUser.getNickname() + " §7has reached §6" +
                                        convertTime(goal.getTime() / 20) + "§7!");
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, plugin.getConfiguration().getGoalsCheckRate() * 20);
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

    public String replacePlaceholders(String input, Player p, Goal goal) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%PLAYER_NAME%", p.getName());
        placeholders.put("%TIME_REQUIRED%", convertTime(goal.getTime() / 20));

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            input = input.replace(entry.getKey(), entry.getValue());
        }

        return input;
    }
}