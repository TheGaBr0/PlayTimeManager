package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PlaytimeReload implements CommandExecutor {
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.reload")) {
            // Reload configurations

            plugin.getConfiguration().reload();

            PlaytimeFormatsConfiguration.getInstance().reload();
            GUIsConfiguration.getInstance().reload();
            CommandsConfiguration.getInstance().reload();

            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " The configuration files have been reloaded"));

            // Reload goals
            goalsManager.clearGoals();
            goalsManager.loadGoals();

            // Reload online users data
            for (Player p : Bukkit.getOnlinePlayers()) {
                OnlineUser user = onlineUsersManager.getOnlineUser(Objects.requireNonNull(p.getPlayer()).getName());
                if (user != null) {
                    user.updatePlayTime();
                    onlineUsersManager.removeOnlineUser(user);
                }
            }
            onlineUsersManager.loadOnlineUsers();

            onlineUsersManager.startGoalCheckSchedule();
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " Goal check schedule has been restarted"));

            dbUsersManager.updateTopPlayersFromDB();

            // Reload join streaks — fetch fresh references after cleanUp() nulls the singleton
            RewardRegistry rewardRegistry = RewardRegistry.getInstance();
            rewardRegistry.clearRewards();
            rewardRegistry.loadRewards();

            JoinStreaksManager.getInstance().cleanUp();
            JoinStreaksManager freshJSM = JoinStreaksManager.getInstance();
            freshJSM.initialize();
            freshJSM.onServerReload();

            if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation", true)) {
                if (!rewardRegistry.isEmpty()) {
                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + " Join streak check schedule has been restarted"));
                } else {
                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + " Join streak check schedule not started: no active rewards found"));
                }
            }

            return true;
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
        }
        return false;
    }
}