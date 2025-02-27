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

        // First check if sender has the base permission
        if (!(sender instanceof Player) || sender.hasPermission("playtime")) {
            // Handle basic command with player name
            if (args.length == 1) {
                // For /playtime <playername>, check if user has the permission to view others
                if (!sender.hasPermission("playtime.others")) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " You don't have permission to execute this command"));
                    return false;
                }

                // Now check if player exists
                if (dbUsersManager.getUserFromNickname(args[0]) == null) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " The player &e" + args[0] + "&7 has never joined the server!"));
                    return false;
                }

                new PlaytimeCommand(sender, args);
                return true;
            }

            // Handle no args case (self stats)
            if (args.length == 0) {
                new PlaytimeCommand(sender, args);
                return true;
            }

            // Handle subcommands
            if (args.length > 1) {
                String targetPlayerName = args[0];
                String subCommand = args[1];

                // Check if the subcommand is valid
                if (!subCommands.contains(subCommand)) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Unknown subcommand: " + subCommand));
                    return false;
                }

                // Check for wildcard reset and special permission
                boolean isWildcardReset = subCommand.equals("reset") && targetPlayerName.equals("*");

                // Check for wildcard permission if trying to use wildcard reset
                if (isWildcardReset && !sender.hasPermission("playtime.others.modify.all")) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " You don't have permission to use wildcards with the reset command"));
                    return false;
                }

                // Check if player exists (only if not a wildcard reset)
                if (!isWildcardReset && dbUsersManager.getUserFromNickname(targetPlayerName) == null) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                            " The player &e" + targetPlayerName + "&7 has never joined the server!"));
                    return false;
                }

                // Process subcommands with specific permissions
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
            }
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have permission to execute this command"));
            return false;
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

                // Add wildcard to tab completion only if user has the right permission
                if (sender.hasPermission("playtime.others.modify.all")) {
                    playerNames.add("*");
                }

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