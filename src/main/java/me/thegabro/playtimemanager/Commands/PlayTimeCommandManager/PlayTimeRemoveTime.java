package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class PlayTimeRemoveTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public PlayTimeRemoveTime(CommandSender sender, String[] args){
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args){

        if(args.length < 3){
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Too few arguments!"));
            return;
        }

        long timeToTicks = Utils.formattedPlaytimeToTicks(args[2]);
        if (timeToTicks == -1L) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Invalid time format: " + args[2]));
            return;
        }

        // Make the time negative since we're removing
        timeToTicks = -timeToTicks;

        long finalTimeToTicks = timeToTicks;
        dbUsersManager.getUserFromNicknameAsyncWithContext(args[0], "remove playtime command", user -> {
            if (user == null) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Player not found: " + args[0]));
                return;
            }

            long oldPlaytime = user.getPlaytime();
            long newArtificialPlaytime = user.getArtificialPlaytime() + finalTimeToTicks;

            user.setArtificialPlaytimeAsync(newArtificialPlaytime, () -> {
                String formattedOldPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime);
                String formattedNewPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime + finalTimeToTicks);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                            " PlayTime of &e" + args[0] +
                            "&7 has been updated from &6" + formattedOldPlaytime +
                            "&7 to &6" + formattedNewPlaytime + "!"));

                    dbUsersManager.updateTopPlayersFromDB();
                });
            });
        });
    }
}