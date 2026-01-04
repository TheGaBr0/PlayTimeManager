package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;

public class PlaytimePercentage implements CommandExecutor {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (sender.hasPermission("playtime.percentage")) {
            if(args.length > 0) {

                long timeToTicks = Utils.formattedPlaytimeToTicks(args[0]);

                if(timeToTicks == -1L){
                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + " Time is not specified correctly!"));
                    return false;
                }

                // Perform the operation asynchronously
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Wait for the update to complete
                        onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                        // Now that updates are complete, get the percentage
                        DecimalFormat df = new DecimalFormat("#.##");
                        df.setRoundingMode(RoundingMode.HALF_UP);
                        Object[] result = DatabaseHandler.getInstance().getStatisticsDAO().getPercentageOfPlayers(timeToTicks);
                        String formattedNumber = df.format(result[0]);

                        // Send the message on the main thread
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                                    " The players with playtime greater than or equal to &6" + args[0] +
                                    " &7are &6" + result[1] + " &7and represent &6" + formattedNumber +
                                    "% &7of the &6" + result[2] + " &7players stored"));
                        });
                    } catch (InterruptedException | ExecutionException e) {
                        // Send error message on the main thread
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                                    " Error while processing command: " + e.getMessage()));
                        });
                        plugin.getLogger().severe("Error in PlaytimePercentage command: " + e.getMessage());
                    }
                });

                return true;
            } else {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("too-few-arguments")));
            }
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
        }
        return false;
    }
}