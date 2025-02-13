package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PlaytimeAverage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final PlayTimeDatabase db = plugin.getDatabase();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.average")){
            sender.sendMessage(Utils.parseComplexHex(plugin.getConfiguration().getPluginPrefix() + " The average play time is:§6 " + Utils.ticksToFormattedPlaytime((long) (Math.ceil(db.getAveragePlaytime())))));
            return true;
        } else {
            sender.sendMessage(Utils.parseComplexHex(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
        }
        return false;
    }
}