package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.ExternalPluginSupport.LuckPerms.LuckPermsManager;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RewardExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final RewardMessageService messageService;

    public RewardExecutor() {
        this.messageService = new RewardMessageService();
    }

    public void processCompletedReward(Player player, RewardSubInstance subInstance) {
        OnlineUser onlineUser = OnlineUsersManager.getInstance().getOnlineUser(player.getName());
        JoinStreakReward reward = JoinStreaksManager.getInstance().getRewardRegistry().getReward(subInstance.mainInstanceID());

        // Logic for expired rewards: only mark as received if the player's current relative
        // join streak meets the condition. This prevents issues when players don't claim
        // rewards in the current cycle - they should only be claimable once in the next cycle.
        if (onlineUser.isExpired(subInstance)) {
            onlineUser.unclaimReward(subInstance);
            try {
                if (onlineUser.getRelativeJoinStreak() >= subInstance.requiredJoins()) {
                    onlineUser.addReceivedReward(subInstance);
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}
        } else {
            // Regular reward - always mark as received
            onlineUser.unclaimReward(subInstance);
            onlineUser.addReceivedReward(subInstance);
        }

        if (plugin.isPermissionsManagerConfigured()) {
            assignPermissionsForReward(onlineUser, reward);
        }

        executeRewardCommands(reward, player);

        messageService.sendRewardRelatedMessage(player, subInstance, reward.getRewardMessage(), 1);

        playRewardSound(player, reward);
    }
    private void assignPermissionsForReward(OnlineUser onlineUser, JoinStreakReward reward) {
        ArrayList<String> permissions = reward.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            try {
                LuckPermsManager.getInstance(plugin).assignRewardPermissions(onlineUser.getUuid(), reward);
            } catch (Exception e) {
                plugin.getLogger().severe(String.format("Failed to assign permissions for join streak reward %d to player %s: %s",
                        reward.getId(), onlineUser.getNickname(), e.getMessage()));
            }
        }
    }

    private void executeRewardCommands(JoinStreakReward reward, Player player) {
        ArrayList<String> commands = reward.getCommands();
        if (commands != null && !commands.isEmpty()) {
            commands.forEach(command -> {
                try {
                    String formattedCommand = formatRewardCommand(command, player, reward);
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                } catch (Exception e) {
                    plugin.getLogger().severe(String.format("Failed to execute command for join streak reward %d: %s",
                            reward.getId(), e.getMessage()));
                }
            });
        }
    }

    private String formatRewardCommand(String command, Player player, JoinStreakReward reward) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("PLAYER_NAME", player.getName());

        return replacePlaceholders(command, replacements).replaceFirst("/", "");
    }

    private void playRewardSound(Player player, JoinStreakReward reward) {
        try {
            String soundName = reward.getRewardSound();
            Sound sound = null;

            try {
                sound = (Sound) Sound.class.getField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                if (plugin.getConfiguration().getBoolean("streak-check-verbose")) {
                    plugin.getLogger().info("Could not find sound directly, attempting fallback: " + e.getMessage());
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning(String.format("Could not find sound '%s' for reward '%s'",
                        soundName, reward.getId()));
            }
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to play sound '%s' for goal '%s': %s",
                    reward.getRewardSound(), reward.getId(), e.getMessage()));
        }
    }

    private String replacePlaceholders(String input, Map<String, String> replacements) {
        String result = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}