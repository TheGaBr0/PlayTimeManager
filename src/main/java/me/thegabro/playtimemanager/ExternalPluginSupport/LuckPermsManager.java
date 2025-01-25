package me.thegabro.playtimemanager.ExternalPluginSupport;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import java.util.UUID;

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

    public void assignPermission(String uuid, String permission) {
        try {
            User user = luckPermsApi.getUserManager().getUser(UUID.fromString(uuid));
            if (user == null) {
                plugin.getLogger().warning("Failed to find LuckPerms user with UUID: " + uuid);
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
            plugin.getLogger().warning("Group " + groupName + " does not exist in LuckPerms");
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
        if (goal.getPermissions() == null || goal.getPermissions().isEmpty()) {
            return;
        }

        for (String permission : goal.getPermissions()) {
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
            plugin.getLogger().warning("Error checking if group exists: " + groupName + " - " + e.getMessage());
            return false;
        }
    }
}