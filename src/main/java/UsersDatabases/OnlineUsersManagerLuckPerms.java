package UsersDatabases;

import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OnlineUsersManagerLuckPerms extends OnlineUsersManager {
    private BukkitTask schedule;
    public OnlineUsersManagerLuckPerms(){
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
                HashMap<String, Long> groups;
                Group groupLuckPerms;
                User userLuckPerms;
                if (plugin.getConfiguration().getLuckPermsCheckVerbose())
                    Bukkit.getConsoleSender().sendMessage("[§6Play§eTime§f]§7 Luckperms check started, refresh rate is "
                            + convertTime(plugin.getConfiguration().getLuckPermsCheckRate()) +
                            ".\n If you find this message annoying you can deactivate it by changing §6luckperms-check-verbose " +
                            "§7in the config.yml");
                // Get groups from configuration
                groups = plugin.getConfiguration().getGroups();

                if (groups != null) {
                    // Iterate through online users
                    for (OnlineUser onlineUser : onlineUsers) {

                        // Iterate through groups
                        for (String group : groups.keySet()) {
                            // Get group from LuckPerms
                            groupLuckPerms = plugin.luckPermsApi.getGroupManager().getGroup(group);
                            if (groupLuckPerms == null) {
                                continue; // Skip to next group if group doesn't exist in LuckPerms
                            }

                            // Check play time requirement for group
                            if (onlineUser.getPlaytime() >= groups.get(group)) {
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
                                        if (groupName.equals(group)) {
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
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 10, 0);
                                        p.sendMessage("[§6Play§eTime§f]§7 Avendo raggiunto §6" +
                                                convertTime(plugin.getConfiguration().getGroupPlayTime(group) / 20)
                                                + "§7 di tempo di gioco " + "§7sei stato promosso a §e" + group + "§7!" +
                                                " Contatta lo staff su §9Discord§7 per ricevere il tuo ruolo anche li!");
                                        Bukkit.getServer().getConsoleSender().sendMessage("[§6Play§eTime§f]§7 User §e"
                                                + onlineUser.getNickname() + " §7has reached §6" +
                                                convertTime(plugin.getConfiguration().getGroupPlayTime(group) / 20) +
                                                " §7so it is now part of §e" + group + " §7group!");
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }.runTaskTimer(plugin, 0, plugin.getConfiguration().getLuckPermsCheckRate() * 20);
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

}
