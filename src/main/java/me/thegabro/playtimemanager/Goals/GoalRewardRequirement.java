package me.thegabro.playtimemanager.Goals;

import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderConditionEvaluator;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.ArrayList;

public class GoalRewardRequirement {
    private List<String> permissions;
    private List<String> placeholderConditions;
    private long time;
    private final PlaceholderConditionEvaluator placeholderConditionEvaluator = PlaceholderConditionEvaluator.getInstance();


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

    // Getters and setters
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