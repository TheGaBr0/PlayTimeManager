package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlayTimeCommandManager implements CommandExecutor, TabCompleter {
    private final List<String> subCommands = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final List<String> resetOptions = new ArrayList<>();

    public PlayTimeCommandManager() {
        subCommands.add("add");
        subCommands.add("remove");
        subCommands.add("reset");
        subCommands.add("stats");

        resetOptions.add("db");
        resetOptions.add("stats");
        resetOptions.add("all");
    }



    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {

        if (!command.getName().equalsIgnoreCase("playtime")) {
            return false;
        }

        if (args.length > 0) {
            String targetPlayerName = args[0];

            // Check if it's a reset command with '*' or '+' wildcard
            boolean isWildcardReset = args.length > 1 &&
                    args[1].equals("reset") &&
                    (targetPlayerName.equals("*") || targetPlayerName.equals("+"));

            // Only validate player existence if it's not a wildcard reset
            if (!isWildcardReset && dbUsersManager.getUserFromNickname(targetPlayerName) == null) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " The player &e" + targetPlayerName + "&7 has never joined the server!"));
                return false;
            }
        }

        if (!(sender instanceof Player) || sender.hasPermission("playtime")) {
            if (args.length <= 1) {
                new PlaytimeCommand(sender, args);
                return true;
            }

            String subCommand = args[1];
            if (!subCommands.contains(subCommand)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Unknown subcommand: " + subCommand));
                return false;
            }

            switch (subCommand) {
                case "stats":
                    if (!sender.hasPermission("playtime.others.stats")) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
                        return false;
                    }
                    new PlayTimeStats(sender, args);
                    return true;

                case "add":
                    if (!sender.hasPermission("playtime.others.modify")) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
                        return false;
                    }
                    new PlayTimeAddTime(sender, args);
                    return true;

                case "remove":
                    if (!sender.hasPermission("playtime.others.modify")) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
                        return false;
                    }
                    new PlayTimeRemoveTime(sender, args);
                    return true;

                case "reset":
                    if (!sender.hasPermission("playtime.others.modify")) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
                        return false;
                    }
                    new PlayTimeResetTime(sender, args);
                    return true;

                default:
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Unknown subcommand: " + subCommand));
                    return false;
            }
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        final List<String> availableCommands = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("playtime.others")) {
                List<String> playerNames = Bukkit.getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else {
                return new ArrayList<>();
            }
        } else if (args.length == 2) {
            if (sender.hasPermission("playtime.others.stats")) {
                availableCommands.add("stats");
            }
            if (sender.hasPermission("playtime.others.modify")) {
                availableCommands.add("add");
                availableCommands.add("remove");
                availableCommands.add("reset");
            }

            StringUtil.copyPartialMatches(args[1], availableCommands, completions);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("reset") && sender.hasPermission("playtime.others.modify")) {
            StringUtil.copyPartialMatches(args[2], resetOptions, completions);
        }

        return completions;
    }
}