package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import me.thegabro.playtimemanager.Commands.PlayTimeReset;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlayTimeCommandManager implements TabExecutor {
    private final List<String> subCommands = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final List<String> resetOptions = new ArrayList<>();
    private final CommandsConfiguration config = CommandsConfiguration.getInstance();
    public PlayTimeCommandManager() {
        subCommands.add("add");
        subCommands.add("remove");
        subCommands.add("reset");

        resetOptions.add("playtime");
        resetOptions.add("server_playtime");
        resetOptions.add("last_seen");
        resetOptions.add("first_join");
        resetOptions.add("joinstreak");
        resetOptions.add("joinstreak_rewards");
        resetOptions.add("goals");
        resetOptions.add("everything");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("playtime")) return false;

        if (!(sender instanceof Player) || sender.hasPermission("playtime")) {

            // Case: /playtime (self stats)
            if (args.length == 0) {
                new PlaytimeCommand(sender, null);
                return true;
            }

            // Case: /playtime <playername>
            if (args.length == 1) {
                if (!sender.hasPermission("playtime.others")) {
                    sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                            config.getString("no-permission")));
                    return false;
                }

                dbUsersManager.getUserFromNicknameAsyncWithContext(args[0], "playtime command", user -> {
                    if (user == null) {
                        sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        config.getString("player-never-joined").replace("%PLAYER%", args[0])));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> new PlaytimeCommand(sender, user));
                });

                return true; // Return immediately, actual execution is async
            }

            // Case: subcommands
            String targetPlayerName = args[0];
            String subCommand = args[1];

            if (!subCommands.contains(subCommand)) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("unknown-subcommand")));
                return false;
            }

            boolean isWildcardReset = subCommand.equals("reset") && targetPlayerName.equals("*");
            if (isWildcardReset && !sender.hasPermission("playtime.others.modify.all")) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                        config.getString("no-permission-wildcard-reset")));
                return false;
            }

            if (!isWildcardReset) {
                dbUsersManager.getUserFromNicknameAsyncWithContext(targetPlayerName, "playtime subcommand", user -> {
                    if (user == null) {
                        sender.sendMessage(Utils.parseColors(config.getString("prefix") +
                                config.getString("player-never-joined").replace("%PLAYER%", targetPlayerName)));
                        return;
                    }

                    // Execute subcommand on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (subCommand) {
                            case "add":
                                if (!sender.hasPermission("playtime.others.modify")) {
                                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
                                    return;
                                }
                                new PlayTimeAddTime(sender, args);
                                break;

                            case "remove":
                                if (!sender.hasPermission("playtime.others.modify")) {
                                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
                                    return;
                                }
                                new PlayTimeRemoveTime(sender, args);
                                break;

                            case "reset":
                                if (!sender.hasPermission("playtime.others.modify")) {
                                    sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
                                    return;
                                }
                                new PlayTimeReset(sender, args);
                                break;
                        }
                    });
                });

                return true; // Return immediately, execution happens asynchronously
            } else {
                // Wildcard reset logic can run immediately
                Bukkit.getScheduler().runTask(plugin, () -> new PlayTimeReset(sender, args));
                return true;
            }
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + config.getString("no-permission")));
            return false;
        }

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
        }  else if (args.length == 2) {
        if (sender.hasPermission("playtime.others.modify")) {
            if (args[0].equals("*")) {
                // Wildcard only supports reset
                availableCommands.add("reset");
            } else {
                // Normal players support all subcommands
                availableCommands.add("add");
                availableCommands.add("remove");
                availableCommands.add("reset");
            }
        }

        StringUtil.copyPartialMatches(args[1], availableCommands, completions);

        } else if (args.length == 3 && args[1].equalsIgnoreCase("reset") && sender.hasPermission("playtime.others.modify")) {
            StringUtil.copyPartialMatches(args[2], resetOptions, completions);
        }

        return completions;
    }
}