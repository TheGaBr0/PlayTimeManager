package me.thegabro.playtimemanager.JoinStreaks.ManagingClasses;

import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RewardMessageService {
    private final PlayTimeManager plugin;

    public RewardMessageService(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void sendRewardRelatedMessage(Player player, RewardSubInstance subInstance, String message, int delaySeconds) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%PLAYER_NAME%", player.getName());
        replacements.put("%REQUIRED_JOINS%", String.valueOf(subInstance.requiredJoins()));

        final Component finalMessage = Utils.parseColors(replacePlaceholders(message, replacements));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            player.sendMessage(finalMessage);
        }, delaySeconds * 20L); // Convert seconds to ticks (20 ticks = 1 second)
    }

    public void sendScheduleActivationMessage(CommandSender sender, boolean activated) {
        if (activated) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The join streak check schedule has been activated"));
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The join streak check schedule has been deactivated"));
        }
    }

    public void sendNextResetMessage(CommandSender sender, Map<String, Object> scheduleInfo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));

        if (scheduleInfo.get("nextReset") != null) {
            Date nextReset = (Date) scheduleInfo.get("nextReset");
            String timeRemaining = (String) scheduleInfo.get("timeRemaining");

            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " Next join streak interval reset scheduled for: &e" + formatter.format(
                    nextReset.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()) + "&7 (in &e" + timeRemaining + "&7)"));
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