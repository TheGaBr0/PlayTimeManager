package me.thegabro.playtimemanager.Commands.PlayTimeCommandManager;

import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import me.thegabro.playtimemanager.Commands.CommandRegistrar;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;



public class PlayTimeCommandManager implements CommandRegistrar {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

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