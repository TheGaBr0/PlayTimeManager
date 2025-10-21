package me.thegabro.playtimemanager.Goals;

import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderConditionEvaluator;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GoalRewardRequirement {
    private List<String> permissions;
    private List<String> placeholderConditions;
    private long time;
    private final PlaceholderConditionEvaluator placeholderConditionEvaluator = PlaceholderConditionEvaluator.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final LuckPermsManager luckPermsManager = LuckPermsManager.getInstance(plugin);

    public GoalRewardRequirement() {
        this.permissions = new ArrayList<>();
        this.placeholderConditions = new ArrayList<>();
        this.time = Long.MAX_VALUE;
    }

    // Check if a player meets all requirements
    public boolean checkRequirements(Player player, long playerTime) {

        // Check time requirement
        if(time != Long.MAX_VALUE){
            if (playerTime < time) {
                return false;
            }
        }

        // Check permissions
        for (String permission : permissions) {
            if (!player.hasPermission(permission)) {
                return false;
            }
        }

        // Check placeholder conditions
        for (String condition : placeholderConditions) {
            if (!placeholderConditionEvaluator.evaluate(player, condition)) {
                return false;
            }
        }

        return true;
    }

    public CompletableFuture<Boolean> checkRequirementsOffline(OfflinePlayer player, long playerTime) {
        UUID uuid = player.getUniqueId();

        if (time != Long.MAX_VALUE && playerTime < time) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> permissionChecks = CompletableFuture.completedFuture(true);

        for (String permission : permissions) {
            permissionChecks = permissionChecks.thenComposeAsync(prev -> {
                if (!prev) return CompletableFuture.completedFuture(false);
                return luckPermsManager.hasPermissionAsync(uuid.toString(), permission);
            });
        }

        return permissionChecks.thenApplyAsync(hasAllPerms -> {
            if (!hasAllPerms) return false;

            for (String condition : placeholderConditions) {
                if (!placeholderConditionEvaluator.evaluate(player, condition)) {
                    return false;
                }
            }

            return true;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check offline requirements for " + player.getName() + ": " + ex.getMessage());
            return false;
        });
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public void addPermission(String permission) {
        this.permissions.add(permission);
    }

    public void removePermission(String permission) {
        this.permissions.remove(permission);
    }

    public List<String> getPlaceholderConditions() {
        return placeholderConditions;
    }

    public void setPlaceholderConditions(List<String> placeholderConditions) {
        this.placeholderConditions = placeholderConditions;
    }

    public void addPlaceholderCondition(String condition) {
        this.placeholderConditions.add(condition);
    }

    public void removePlaceholderCondition(String condition) {
        this.placeholderConditions.remove(condition);
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}