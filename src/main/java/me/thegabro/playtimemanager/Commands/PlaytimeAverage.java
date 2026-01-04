package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PlaytimeAverage implements CommandExecutor {
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        //TODO: ASYNC
        if (sender.hasPermission("playtime.average")){
            sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                    " The average playtime is:&6 " + Utils.ticksToFormattedPlaytime( (long) (Math.ceil(DatabaseHandler.getInstance().getStatisticsDAO().getAveragePlaytime())))));
            return true;
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
        }
        return false;
    }
}