package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;


public class PlaytimeAverage implements CommandRegistrar {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final PlayTimeDatabase db = plugin.getDatabase();

    public void registerCommands() {
        new CommandAPICommand("playtimeaverage")
                .withHelp("View average playtime", "Display the average playtime across all players on the server")
                .withUsage("/playtimeaverage", "/ptavg")
                .withAliases("ptavg")
                .withPermission(CommandPermission.fromString("playtime.average"))
                .executes((sender, args) -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " The average playtime is:&6 " + Utils.ticksToFormattedPlaytime((long) (Math.ceil(db.getAveragePlaytime())))));
                })
                .register();
        }
}
