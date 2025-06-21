package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.StringArgument;
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

public class PlaytimePercentage implements CommandRegistrar {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    public void registerCommands() {
        new CommandAPICommand("playtimepercentage")
                .withPermission(CommandPermission.fromString("playtime.percentage"))
                .withArguments(new StringArgument("time"))
                .executes((sender, args) -> {
                    String time = (String) args.get("time");
                    long timeToTicks = Utils.formattedPlaytimeToTicks(time);
                    if (timeToTicks == -1L) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid time format: " + time));
                    }else{
                        executePercentage(sender, timeToTicks, time);
                    }
                })
                .register();
    }

    public void executePercentage(CommandSender sender, long time, String timeFormatted) {

        // Perform the operation asynchronously
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Wait for the update to complete
                onlineUsersManager.updateAllOnlineUsersPlaytime().get();

                // Now that updates are complete, get the percentage
                DecimalFormat df = new DecimalFormat("#.##");
                df.setRoundingMode(RoundingMode.HALF_UP);
                Object[] result = plugin.getDatabase().getPercentageOfPlayers(time);
                String formattedNumber = df.format(result[0]);

                // Send the message on the main thread
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " The players with playtime greater than or equal to &6" + timeFormatted +
                            " &7are &6" + result[1] + " &7and represent &6" + formattedNumber +
                            "% &7of the &6" + result[2] + " &7players stored"));
                });
            } catch (InterruptedException | ExecutionException e) {
                // Send error message on the main thread
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " Error while processing command: " + e.getMessage()));
                });
                plugin.getLogger().severe("Error in PlaytimePercentage command: " + e.getMessage());
            }
        });

    }
}
