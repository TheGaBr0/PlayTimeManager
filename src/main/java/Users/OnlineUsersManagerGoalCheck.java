package Users;

import Goals.Goal;
import Goals.GoalManager;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class OnlineUsersManagerGoalCheck extends OnlineUsersManager {
    private BukkitTask schedule;
    private String configSound, configMessage;
    public OnlineUsersManagerGoalCheck(){
        super();

        restartSchedule();
    }

    public void restartSchedule(){
        // Cancel the existing schedule
        if (schedule != null) {
            schedule.cancel();
        }

        // Create a new task and assign it to the schedule variable
        schedule = new BukkitRunnable() {
            public void run() {
                Player p;
                Group groupLuckPerms;
                User userLuckPerms;
                if (plugin.getConfiguration().getGoalsCheckVerbose())
                    Bukkit.getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 Goal check started, refresh rate is "
                            + convertTime(plugin.getConfiguration().getGoalsCheckRate()) +
                            ".\n If you find this message annoying you can deactivate it by changing §6goal-check-verbose " +
                            "§7in the config.yml");
                // Get groups from configuration
                List<String> groups = GoalManager.getGoalsGroups();

                if (groups.isEmpty()) {
                    schedule.cancel();
                    Bukkit.getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 No goal has been detected, " +
                            "goal check canceled.");

                }

                // Iterate through online users
                for (OnlineUser onlineUser : onlineUsers) {

                    // Iterate through groups
                    for (Goal goal : GoalManager.getGoals()) {
                        // Get group from LuckPerms
                        groupLuckPerms = plugin.luckPermsApi.getGroupManager().getGroup(goal.getLPGroup());
                        if (groupLuckPerms == null) {
                            continue; // Skip to next group if group doesn't exist in LuckPerms
                        }

                        // Check play time requirement for group
                        if (onlineUser.getPlaytime() >= goal.getTime()) {
                            // Get LuckPerms user
                            userLuckPerms = plugin.luckPermsApi.getUserManager().getUser(UUID.fromString(onlineUser.getUuid()));
                            if (userLuckPerms == null) {
                                continue; // Skip to next group if user doesn't exist in LuckPerms
                            }

                            // Check if user already has the group
                            boolean hasGroup = false;
                            for (Node node : userLuckPerms.getNodes()) {
                                if (node.getKey().startsWith("group.")) {
                                    String groupName = node.getKey().substring(6);
                                    if (groupName.equals(goal.getLPGroup())) {
                                        hasGroup = true;
                                        break;
                                    }
                                }
                            }

                            if (!hasGroup) {
                                // Add group to user's LuckPerms data
                                InheritanceNode node = InheritanceNode.builder(groupLuckPerms).value(true).build();
                                userLuckPerms.data().add(node);
                                plugin.luckPermsApi.getUserManager().saveUser(userLuckPerms);

                                // Send messages to player and console
                                p = Bukkit.getPlayerExact(onlineUser.getNickname());

                                if (p != null) {
                                    try{
                                        configSound = goal.getGoalSound();
                                        plugin.getLogger().info(configSound);
                                        Sound sound = Sound.valueOf(configSound);
                                        p.playSound(p.getLocation(), sound, 10, 0);
                                    }catch(IllegalArgumentException exception){
                                        plugin.getLogger().severe(configSound + " is not a valid argument for goal-sound" +
                                                "setting in "+goal.getName()+".yaml");
                                    }

                                    configMessage = replacePlaceholders(goal.getGoalMessage(), p, goal);

                                    p.sendMessage(configMessage);

                                    Bukkit.getServer().getConsoleSender().sendMessage("[§6PlayTime§eManager§f]§7 User §e"
                                            + onlineUser.getNickname() + " §7has reached §6" +
                                            convertTime(goal.getTime() / 20) +
                                            " §7so it is now part of §e" + goal.getLPGroup() + " §7group!");
                                }
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
        // Create a map for the placeholders and their corresponding values
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%PLAYER_NAME%", p.getName());
        placeholders.put("%GROUP_NAME%", goal.getLPGroup());

        // Calculate TIME_REQUIRED
        String playTimeSeconds = convertTime(goal.getTime() / 20);

        placeholders.put("%TIME_REQUIRED%", playTimeSeconds);

        // Replace placeholders in the input string
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            input = input.replace(entry.getKey(), entry.getValue());
        }

        return input;
    }

}
