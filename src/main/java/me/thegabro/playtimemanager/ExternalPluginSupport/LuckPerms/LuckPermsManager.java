package me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;

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
            try {
                luckPermsApi = LuckPermsProvider.get();
                instance = new LuckPermsManager(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize LuckPerms integration: " + e.getMessage());
                throw e;
            }
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
                    }).exceptionally(throwable -> {
                        plugin.getLogger().warning("Failed to get prefix for UUID " + uuid + ": " + throwable.getMessage());
                        return "";
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get prefix for UUID " + uuid + ": " + e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }

    public void assignPermission(String uuid, String permission) {
        try {
            User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
            if (user == null) {
                plugin.getLogger().warning("User not found for UUID: " + uuid);
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
        try {
            Group group = luckPermsApi.getGroupManager().getGroup(groupName);
            if (group == null) {
                plugin.getLogger().warning("Group not found: " + groupName);
                return;
            }

            InheritanceNode node = InheritanceNode.builder(group).value(true).build();
            user.data().add(node);
            luckPermsApi.getUserManager().saveUser(user);
            plugin.getLogger().info("Assigned group " + groupName + " to user " + user.getUsername());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to assign group " + groupName + " to user " + user.getUsername() + ": " + e.getMessage());
        }
    }

    private void assignDirectPermission(User user, String permission) {
        try {
            Node permissionNode = Node.builder(permission)
                    .value(true)
                    .build();
            user.data().add(permissionNode);
            luckPermsApi.getUserManager().saveUser(user);
            plugin.getLogger().info("Assigned permission " + permission + " to user " + user.getUsername());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to assign permission " + permission + " to user " + user.getUsername() + ": " + e.getMessage());
        }
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
        try {
            User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
            if (user == null) {
                return false;
            }
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check permission " + permission + " for UUID " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isInGroup(String uuid, String groupName) {
        try {
            User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
            if (user == null) {
                return false;
            }
            return user.getCachedData().getPermissionData().checkPermission("group." + groupName).asBoolean();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check group " + groupName + " for UUID " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public boolean groupExists(String groupName) {
        try {
            return luckPermsApi.getGroupManager().getGroup(groupName) != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check if group exists " + groupName + ": " + e.getMessage());
            return false;
        }
    }
}