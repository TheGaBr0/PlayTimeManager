package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.reload")) {
            // Reload configuration
            plugin.getConfiguration().reload();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The configuration file has been reloaded"));

            // Reload goals
            goalsManager.clearGoals();
            goalsManager.loadGoals();

            //reload online users data
            for(Player p : Bukkit.getOnlinePlayers()){
                onlineUsersManager.removeOnlineUser(onlineUsersManager.getOnlineUser(Objects.requireNonNull(p.getPlayer()).getName()));
            }
            onlineUsersManager.loadOnlineUsers();

            // Restart LuckPerms schedule if applicable
            onlineUsersManager.startGoalCheckSchedule();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Goal check schedule has been restarted"));

            dbUsersManager.updateTopPlayersFromDB();

            return true;
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
        }
        return false;
    }
}