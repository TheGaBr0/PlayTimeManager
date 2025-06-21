package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import me.thegabro.playtimemanager.Commands.CommandRegistrar;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


public class PlayTimeCommandManager implements CommandRegistrar {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public Argument<String> customPlayerArgument(String nodeName) {
        return new CustomArgument<>(new StringArgument(nodeName), info -> {
            // Check if player exists in database (as your command logic requires)
            if (dbUsersManager.getUserFromNickname(info.input()) == null) {
                throw CustomArgument.CustomArgumentException.fromMessageBuilder(
                        new CustomArgument.MessageBuilder("Player has never joined: ").appendArgInput());
            } else {
                return info.input(); // Return the input string as-is
            }
        }).replaceSuggestions(ArgumentSuggestions.strings(info -> {
            // Only suggest online players
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toArray(String[]::new);
        }));
    }

    public void registerCommands() {
        // Base command: /playtime (self stats)
        new CommandTree("playtime")
                .withPermission(CommandPermission.fromString("playtime"))
                .executesConsole((console, args) -> {
                    console.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command."));
                })
                .executesPlayer((sender, args) -> {
                    try {
                        new PlaytimeCommand(sender);
                    } catch (Exception e) {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                " &cAn error occurred while executing the command."));
                        plugin.getLogger().severe("Error executing playtime command: " + e.getMessage());
                        e.printStackTrace();
                    }
                })
                .then(customPlayerArgument("player") // Command: /playtime <player> (view other player's stats)
                        .withPermission(CommandPermission.fromString("playtime.others"))
                        .executes((sender, args) -> {
                            try {
                                String playerName = (String) args.get("player");

                                // Check if player exists
                                if (dbUsersManager.getUserFromNickname(playerName) == null) {
                                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                            " The player &e" + playerName + "&7 has never joined the server!"));
                                    return;
                                }

                                new PlaytimeCommand(sender, playerName);
                            } catch (Exception e) {
                                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                        " &cAn error occurred while executing the command."));
                                plugin.getLogger().severe("Error executing playtime command: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                        .then(new LiteralArgument("add")
                            .withPermission(CommandPermission.fromString("playtime.others.modify"))
                            .then(new StringArgument("time")
                                    .executes((sender, args) -> {
                                        try {
                                            String playerName = (String) args.get("player");
                                            String time = (String) args.get("time");

                                            DBUser user = dbUsersManager.getUserFromNickname(playerName);

                                            if (user == null) {
                                                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                        " The player &e" + playerName + "&7 has never joined the server!"));
                                                return;
                                            }

                                            new PlayTimeAddTime(sender, user, time);
                                        } catch (Exception e) {
                                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                    " &cAn error occurred while executing the command."));
                                            plugin.getLogger().severe("Error executing playtime add command: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    })
                            )
                        )
                        .then(new LiteralArgument("remove")
                            .withPermission(CommandPermission.fromString("playtime.others.modify"))
                            .then(new StringArgument("time")
                                .executes((sender, args) -> {
                                    try {
                                        String playerName = (String) args.get("player");
                                        String time = (String) args.get("time");

                                        DBUser user = dbUsersManager.getUserFromNickname(playerName);

                                        if (user == null) {
                                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                    " The player &e" + playerName + "&7 has never joined the server!"));
                                            return;
                                        }

                                        // Fixed: changed "add" to "remove"
                                        new PlayTimeRemoveTime(sender, user, time);
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime remove command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                            )
                        )
                        .then(new LiteralArgument("stats")
                                .withPermission(CommandPermission.fromString("playtime.others.stats"))
                                .executes((sender, args) -> {
                                    try {
                                        String playerName = (String) args.get("player");

                                        DBUser user = dbUsersManager.getUserFromNickname(playerName);

                                        if (user == null) {
                                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                    " The player &e" + playerName + "&7 has never joined the server!"));
                                            return;
                                        }

                                        // Fixed: changed "add" to "remove"
                                        new PlayTimeStats(sender, user);
                                    } catch (Exception e) {
                                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                " &cAn error occurred while executing the command."));
                                        plugin.getLogger().severe("Error executing playtime remove command: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                })
                        )
                )
                .register();
    }


}