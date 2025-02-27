package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class PlaytimePercentage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.percentage")) {
            if(args.length > 0) {

                long timeToTicks = Utils.formattedPlaytimeToTicks(args[0]);

                if(timeToTicks == -1L){
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Time is not specified correctly!"));
                    return false;
                }

                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.HALF_UP);
                onlineUsersManager.updateAllOnlineUsersPlaytime();
                Object[] result = plugin.getDatabase().getPercentageOfPlayers(timeToTicks);
                String formattedNumber = df.format(result[0]);
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The players with playtime greater than or equal to &6" + args[0] +
                        " &7are &6" + result[1] + " &7and represent &6" + formattedNumber + "% &7of the &6" + result[2] + " &7players stored"));
                return true;
            } else {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Missing arguments"));
            }
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
        }
        return false;
    }
}