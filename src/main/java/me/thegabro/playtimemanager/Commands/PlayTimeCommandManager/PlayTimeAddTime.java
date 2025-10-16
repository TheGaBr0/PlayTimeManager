package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class PlayTimeAddTime {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    public PlayTimeAddTime(CommandSender sender, String[] args) {
        execute(sender, args);
    }

    public void execute(CommandSender sender, String[] args) {

        if (args.length < 3) {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("too-few-arguments")));
            return;
        }

        long timeToTicks = Utils.formattedPlaytimeToTicks(args[2]);
        if (timeToTicks == -1L) {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("invalid-time-format").replace("%TIME%", args[2])));
            return;
        }

        dbUsersManager.getUserFromNicknameAsyncWithContext(args[0], "add playtime command", user -> {
            if (user == null) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        config.getString("player-never-joined").replace("%PLAYER%", args[0]) ));
                return;
            }

            long oldPlaytime = user.getPlaytime();
            long newArtificialPlaytime = user.getArtificialPlaytime() + timeToTicks;

            if (newArtificialPlaytime < 0) { // Overflow check
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("overflow-error")));
                return;
            }

            user.setArtificialPlaytimeAsync(newArtificialPlaytime, () -> {
                String formattedOldPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime);
                String formattedNewPlaytime = Utils.ticksToFormattedPlaytime(oldPlaytime + timeToTicks);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                            config.getString("playtime.updated")
                                    .replace("%PLAYER_NAME%", args[0])
                                    .replace("%OLD_TIME%", formattedOldPlaytime)
                                    .replace("%NEW_TIME%", formattedNewPlaytime)
                    ));

                    dbUsersManager.updateTopPlayersFromDB();
                });
            });
        });
    }
}