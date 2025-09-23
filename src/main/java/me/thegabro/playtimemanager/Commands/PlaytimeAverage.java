package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PlaytimeAverage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler db = DatabaseHandler.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        //TODO: ASYNC
        if (sender.hasPermission("playtime.average")){
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The average playtime is:&6 " + Utils.ticksToFormattedPlaytime( (long) (Math.ceil(db.getStatisticsDAO().getAveragePlaytime())))));
            return true;
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have the permission to execute this command"));
        }
        return false;
    }
}