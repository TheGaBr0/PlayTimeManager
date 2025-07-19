package me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PermissionChangeListener {

    private final PlayTimeManager plugin;
    private final LuckPerms luckPerms;
    private final List<EventSubscription<?>> eventSubscriptions = new ArrayList<>();

    public PermissionChangeListener(PlayTimeManager plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void register() {
        try {
            EventBus eventBus = luckPerms.getEventBus();

            // Listen for nodes being added
            eventSubscriptions.add(eventBus.subscribe(plugin, NodeAddEvent.class, new NodeAddHandler()));

            // Listen for nodes being removed
            eventSubscriptions.add(eventBus.subscribe(plugin, NodeRemoveEvent.class, new NodeRemoveHandler()));

            plugin.getLogger().info("Successfully registered PermissionChangeListener for node add/remove events");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register PermissionChangeListener: " + e.getMessage());
        }
    }

    public void unregister() {
        for (EventSubscription<?> subscription : eventSubscriptions) {
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception e) {
                    // Silently handle any exceptions during cleanup
                }
            }
        }
        eventSubscriptions.clear();
        plugin.getLogger().info("Unregistered PermissionChangeListener");
    }

    private boolean isRelevantNode(Node node) {
        // Check if this node could affect the playtime.hidefromleaderboard permission
        if (node.getType() == NodeType.PERMISSION) {
            PermissionNode permNode = (PermissionNode) node;
            String permission = permNode.getPermission();

            // Direct permission match or wildcard that could include our permission
            return permission.equals("playtime.hidefromleaderboard");
        }

        // Group inheritance changes can affect inherited permissions
        if (node.getType() == NodeType.INHERITANCE) {
            return true;
        }

        return false;
    }

    private void handlePermissionChange(User user) {
        if (user == null) return;

        String uuid = user.getUniqueId().toString();

        // Check if the user has the playtime.hidefromleaderboard permission
        boolean hasPermission = user.getCachedData()
                .getPermissionData(QueryOptions.nonContextual())
                .checkPermission("playtime.hidefromleaderboard")
                .asBoolean();

        // Update the database entry
        // Run async to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DBUser dbUser = DBUsersManager.getInstance().getUserFromUUID(uuid);
                if (dbUser != null && dbUser.isHiddenFromLeaderboard() != hasPermission) {
                    dbUser.setHiddenFromLeaderboard(hasPermission);
                    DBUsersManager.getInstance().updateTopPlayersFromDB();

                    plugin.getLogger().info("Updated leaderboard visibility for player " + uuid +
                            " (hidden: " + hasPermission + ") due to permission change");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to update leaderboard visibility for player " + uuid + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private class NodeAddHandler implements Consumer<NodeAddEvent> {
        @Override
        public void accept(NodeAddEvent event) {
            // Only handle user targets
            if (!(event.getTarget() instanceof User user)) return;

            // Only process if the added node could affect our specific permission
            if (isRelevantNode(event.getNode())) {
                handlePermissionChange(user);
            }
        }
    }

    private class NodeRemoveHandler implements Consumer<NodeRemoveEvent> {
        @Override
        public void accept(NodeRemoveEvent event) {
            // Only handle user targets
            if (!(event.getTarget() instanceof User user)) return;

            // Only process if the removed node could affect our specific permission
            if (isRelevantNode(event.getNode())) {
                handlePermissionChange(user);
            }
        }
    }
}