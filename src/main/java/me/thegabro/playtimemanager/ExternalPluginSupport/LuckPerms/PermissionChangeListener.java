package me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

import java.util.function.Consumer;

public class PermissionChangeListener {

    private final PlayTimeManager plugin;
    private final LuckPerms luckPerms;
    private EventSubscription<NodeMutateEvent> eventSubscription;

    public PermissionChangeListener(PlayTimeManager plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public void register() {
        try {
            EventBus eventBus = luckPerms.getEventBus();

            // Listen for node mutation events (when permissions change)
            eventSubscription = eventBus.subscribe(NodeMutateEvent.class, new PermissionMutateHandler());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register PermissionChangeListener: " + e.getMessage());
        }
    }

    public void unregister() {
        if (eventSubscription != null) {
            try {
                eventSubscription.close();
                eventSubscription = null;
            } catch (Exception e) {
                // Silently handle any exceptions during cleanup
            }
        }
    }

    private class PermissionMutateHandler implements Consumer<NodeMutateEvent> {
        @Override
        public void accept(NodeMutateEvent event) {
            if (!(event.getTarget() instanceof User user)) return;

            String uuid = user.getUniqueId().toString();
            plugin.getLogger().info("aaa");
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
                    if(dbUser.isHiddenFromLeaderboard() != hasPermission){
                        dbUser.setHiddenFromLeaderboard(hasPermission);
                        DBUsersManager.getInstance().updateTopPlayersFromDB();
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to update leaderboard visibility for player " + uuid + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }
}