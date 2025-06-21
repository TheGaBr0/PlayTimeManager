package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;

public class PlayTimeAddTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public PlayTimeAddTime(CommandSender sender, DBUser target, String time) {
        PlayTimeManager plugin = PlayTimeManager.getInstance();
        if(time == null)
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Too few arguments!"));

        long timeToTicks = Utils.formattedPlaytimeToTicks(time);
        if (timeToTicks == -1L) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid time format: " + time));
            return;
        }

        long oldPlaytime = target.getPlaytime();

        long newArtificialPlaytime = target.getArtificialPlaytime() + timeToTicks;
        if (newArtificialPlaytime < 0) { // Overflow check
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Error: Time value too large!"));
            return;
        }

        String formattedOldPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime);
        target.setArtificialPlaytime(newArtificialPlaytime);
        String formattedNewPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime + timeToTicks);

        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " PlayTime of &e" + target +
                "&7 has been updated from &6" + formattedOldPlaytime + "&7 to &6" + formattedNewPlaytime + "!"));

        DBUsersManager dbUsersManager = DBUsersManager.getInstance();
        dbUsersManager.updateTopPlayersFromDB();
    }


}