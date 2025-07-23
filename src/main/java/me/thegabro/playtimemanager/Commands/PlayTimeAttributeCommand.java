package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.SQLiteDB.PlayTimeDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlayTimeAttributeCommand implements CommandExecutor, TabCompleter {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final PlayTimeDatabase db = plugin.getDatabase();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.others.attributes")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " You don't have permission to execute this command"));
            return false;
        }

        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " Usage: /playtimeattribute <player> <attribute> [true|false]"));
            return false;
        }

        String playerName = args[0];
        String attribute = args[1].toLowerCase();

        // Validate attribute
        if (!attribute.equals("hidefromleaderboard")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " Invalid attribute. Available: hidefromleaderboard"));
            return false;
        }

        DBUser user = DBUsersManager.getInstance().getUserFromNickname(playerName);
        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return false;
        }

        boolean newValue;

        if (args.length == 3) {
            // Value provided - validate it
            String value = args[2].toLowerCase();
            if (!value.equals("true") && !value.equals("false")) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Invalid value. Use: true or false"));
                return false;
            }
            newValue = Boolean.parseBoolean(value);
        } else {
            // No value provided - toggle current value
            newValue = !getCurrentAttributeValue(user, attribute);
        }

        // Update the attribute in database
        updatePlayerAttribute(sender, user, attribute, newValue, playerName);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.attribute")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete player names
            String partial = args[0].toLowerCase();
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            });
        } else if (args.length == 2) {
            // Complete attributes
            String partial = args[1].toLowerCase();
            if ("hidefromleaderboard".startsWith(partial)) {
                completions.add("hidefromleaderboard");
            }
        } else if (args.length == 3) {
            // Complete true/false values
            String partial = args[2].toLowerCase();
            if ("true".startsWith(partial)) {
                completions.add("true");
            }
            if ("false".startsWith(partial)) {
                completions.add("false");
            }
        }

        return completions;
    }

    private boolean getCurrentAttributeValue(DBUser user, String attribute) {
        switch (attribute) {
            case "hidefromleaderboard":
                return DBUsersManager.getInstance().getPlayersHiddenFromLeaderBoard().contains(user.getNickname());
            default:
                return false;
        }
    }

    private void updatePlayerAttribute(CommandSender sender, DBUser user, String attribute, boolean value, String playerName) {
        try {
            switch (attribute) {
                case "hidefromleaderboard":
                    handleHiddenFromLeaderboardAttribute(sender, user, value, playerName);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error updating player attribute: " + e.getMessage());
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " Failed to update player attribute"));
        }
    }

    private void handleHiddenFromLeaderboardAttribute(CommandSender sender, DBUser user, boolean hide, String playerName) {
        // Run async to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {

                if(hide){
                    DBUsersManager.getInstance().hidePlayerFromLeaderBoard(user.getNickname());
                }else{
                    DBUsersManager.getInstance().unhidePlayerFromLeaderBoard(user.getNickname());
                }

                // Wait for the database update to complete, then update leaderboard
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        DBUsersManager.getInstance().updateTopPlayersFromDB();
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                                " Successfully set hidefromleaderboard to " + hide + " for player " + playerName));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to update leaderboard after setting attribute: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to update leaderboard visibility for player " + user.getNickname() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}