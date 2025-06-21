package me.thegabro.playtimemanager.ExternalPluginSupport;

import dev.jorel.commandapi.CommandAPI;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsManager {
    private final PlayTimeManager plugin;
    private static LuckPerms luckPermsApi;
    private static LuckPermsManager instance;

    private LuckPermsManager(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public static LuckPermsManager getInstance(PlayTimeManager plugin) {
        if (instance == null) {
            luckPermsApi = LuckPermsProvider.get();
            instance = new LuckPermsManager(plugin);
        }
        return instance;
    }

    public LuckPerms getLuckPermsApi() {
        return luckPermsApi;
    }

    public CompletableFuture<String> getPrefixAsync(String uuid) {
        try {
            return luckPermsApi.getUserManager().loadUser(UUID.fromString(uuid))
                    .thenApplyAsync(user -> {
                        String prefix = user.getCachedData().getMetaData().getPrefix();
                        return prefix != null ? prefix : "";
                    }).exceptionally(throwable -> "");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("");
        }
    }

    public void assignPermission(String uuid, String permission) {
        try {
            User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
            if (user == null) {
                return;
            }

            if (permission.startsWith("group.")) {
                assignGroup(user, permission.substring(6));
            } else {
                assignDirectPermission(user, permission);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to assign permission " + permission + " to UUID " + uuid + ": " + e.getMessage());
        }
    }

    private void assignGroup(User user, String groupName) {
        Group group = luckPermsApi.getGroupManager().getGroup(groupName);
        if (group == null) {
            return;
        }

        InheritanceNode node = InheritanceNode.builder(group).value(true).build();
        user.data().add(node);
        luckPermsApi.getUserManager().saveUser(user);
    }

    private void assignDirectPermission(User user, String permission) {
        Node permissionNode = Node.builder(permission)
                .value(true)
                .build();
        user.data().add(permissionNode);
        luckPermsApi.getUserManager().saveUser(user);
    }

    public void assignGoalPermissions(String uuid, Goal goal) {
        if (goal.getRewardPermissions() == null || goal.getRewardPermissions().isEmpty()) {
            return;
        }

        for (String permission : goal.getRewardPermissions()) {
            assignPermission(uuid, permission);
        }
    }

    public void assignRewardPermissions(String uuid, JoinStreakReward reward) {
        if (reward.getPermissions() == null || reward.getPermissions().isEmpty()) {
            return;
        }

        for (String permission : reward.getPermissions()) {
            assignPermission(uuid, permission);
        }
    }

    public boolean hasPermission(String uuid, String permission) {
        User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    public boolean isInGroup(String uuid, String groupName) {
        User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission("group." + groupName).asBoolean();
    }

    public boolean groupExists(String groupName) {
        try {
            return luckPermsApi.getGroupManager().getGroup(groupName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public int getPlayersWithPermissionCount(String permission) {
        int count = 0;

        try {
            // Get all loaded users from LuckPerms
            Set<User> loadedUsers = luckPermsApi.getUserManager().getLoadedUsers();

            for (User user  : loadedUsers) {
                if (user != null) {
                    // Check if the user has the specified permission
                    boolean hasPermission = user.getCachedData().getPermissionData()
                            .checkPermission(permission).asBoolean();

                    if (hasPermission) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to count players with permission " + permission + ": " + e.getMessage());
        }

        return count;
    }

    public void registerPermissionListener() {
        EventBus eventBus = luckPermsApi.getEventBus();
        eventBus.subscribe(NodeMutateEvent.class, event -> {
            if (!(event.getTarget() instanceof User user)) return;

            UUID uuid = user.getUniqueId();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                CommandAPI.updateRequirements(player);
            }
        });
    }


}