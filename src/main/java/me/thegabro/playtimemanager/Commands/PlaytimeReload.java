package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Goals.GoalsManager;
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
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.reload")) {
            // Reload configurations
            plugin.getConfiguration().reload();

            PlaytimeFormatsConfiguration.getInstance().reload();
            GUIsConfiguration.getInstance().reload();
            CommandsConfiguration.getInstance().reload();

            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " The configuration files have been reloaded"));

            // Reload goals
            goalsManager.clearGoals();
            goalsManager.loadGoals();

            //reload online users data
            for(Player p : Bukkit.getOnlinePlayers()) {
                OnlineUser user = onlineUsersManager.getOnlineUser(Objects.requireNonNull(p.getPlayer()).getName());
                if (user != null) {
                    // Update DB with the latest playtime before removing
                    user.updatePlayTime();
                    // Now remove the user
                    onlineUsersManager.removeOnlineUser(user);
                }
            }
            onlineUsersManager.loadOnlineUsers();


            onlineUsersManager.startGoalCheckSchedule();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Goal check schedule has been restarted"));

            dbUsersManager.updateTopPlayersFromDB();

            joinStreaksManager.getRewardRegistry().clearRewards();
            joinStreaksManager.getRewardRegistry().loadRewards();

            joinStreaksManager.getCycleScheduler().initialize();

            // Only start the task if it's enabled in config
            if (plugin.getConfiguration().getBoolean("rewards-check-schedule-activation")) {
                joinStreaksManager.getCycleScheduler().getNextSchedule();
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Join streak check schedule has been restarted"));
            }

            return true;
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have the permission to execute this command"));
        }
        return false;
    }
}