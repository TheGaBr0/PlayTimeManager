package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.CommandSender;

public class PlayTimeAddTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public PlayTimeAddTime(CommandSender sender, String[] args) {
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args) {

        if (args.length < 3) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Too few arguments!"));
            return;
        }

        long timeToTicks = Utils.formattedPlaytimeToTicks(args[2]);
        if (timeToTicks == -1L) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Invalid time format: " + args[2]));
            return;
        }

        DBUser user = dbUsersManager.getUserFromNickname(args[0]);
        long oldPlaytime = user.getPlaytime();

        long newArtificialPlaytime = user.getArtificialPlaytime() + timeToTicks;
        if (newArtificialPlaytime < 0) { // Overflow check
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Error: Time value too large!"));
            return;
        }

        String formattedOldPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime);
        user.setArtificialPlaytime(newArtificialPlaytime);
        String formattedNewPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime + timeToTicks);

        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " PlayTime of &e" + args[0] +
                "&7 has been updated from &6" + formattedOldPlaytime + "&7 to &6" + formattedNewPlaytime + "!"));

        dbUsersManager.updateTopPlayersFromDB();
    }
}