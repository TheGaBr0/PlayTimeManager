package UsersDatabases;

import me.thegabro.playtimemanager.PlayTimeManager;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class UsersManagerLuckPerms extends UsersManager{

    private final long timeInSeconds = 60;

    public UsersManagerLuckPerms(){
        super();

        new BukkitRunnable() {
            public void run() {
                Player p;
                HashMap<String, Long> groups;
                Group groupLuckPerms;
                net.luckperms.api.model.user.User userLuckPerms;

                // Get groups from configuration
                groups = plugin.getConfiguration().getGroups();

                if(groups != null){
                    // Iterate through online users
                    for (User user : onlineUsers) {

                        // Iterate through groups
                        for (String group : groups.keySet()) {
                            // Get group from LuckPerms
                            groupLuckPerms = plugin.luckPermsApi.getGroupManager().getGroup(group);
                            if (groupLuckPerms == null) {
                                continue; // Skip to next group if group doesn't exist in LuckPerms
                            }

                            // Check play time requirement for group
                            if (user.getPlayTime() >= groups.get(group)) {
                                // Get LuckPerms user
                                userLuckPerms = plugin.luckPermsApi.getUserManager().getUser(UUID.fromString(user.getUuid()));
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
                                    p = Bukkit.getPlayerExact(user.getName());

                                    if(p != null){
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 10, 0);
                                        p.sendMessage("[§6Play§eTime§f]§7 Avendo raggiunto §6" +
                                                plugin.getPlayTimeDB().convertTime(plugin.getConfiguration().getGroupPlayTime(group) / 20)
                                                + "§7 di tempo di gioco " + "§7sei stato promosso a §e" + group + "§7!" +
                                                " Contatta lo staff su §9Discord§7 per ricevere il tuo ruolo anche li!");
                                        Bukkit.getServer().getConsoleSender().sendMessage("[§6Play§eTime§f]§7 User §e"
                                                + user.getName() + " §7has reached §6" +
                                                plugin.getPlayTimeDB().convertTime(plugin.getConfiguration().getGroupPlayTime(group) / 20) +
                                                " §7so it is now part of §e" + group + " §7group!");
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }.runTaskTimer(plugin,0,timeInSeconds * 20);
    }

}
