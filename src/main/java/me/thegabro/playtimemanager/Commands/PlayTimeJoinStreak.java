package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import me.thegabro.playtimemanager.GUIs.JoinStreak.AllJoinStreakRewardsGui;
import me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class PlayTimeJoinStreak implements CommandRegistrar {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();

    public void registerCommands(){

        new CommandTree("playtimejoinstreak")
                .withAliases("ptjsk")
                .withPermission(CommandPermission.fromString("playtime.joinstreak"))
                .executesConsole((console, args) -> {
                    console.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command."));
                })
                .executesPlayer((player, args) -> {
                    AllJoinStreakRewardsGui gui = new AllJoinStreakRewardsGui();
                    gui.openInventory(player);
                })
                .then(new LiteralArgument("seeplayer")
                        .withPermission(CommandPermission.fromString("playtime.joinstreak.seeplayer"))
                                .then(customPlayerArgument("target")
                                .executesConsole((console, args) -> {
                                    console.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command."));
                                })
                                .executesPlayer((player, args) -> {
                                    String playerTarget = (String) args.get("target");
                                    DBUser user = dbUsersManager.getUserFromNickname(playerTarget);

                                    String sessionToken = UUID.randomUUID().toString();
                                    plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);

                                    RewardsInfoGui rewardsGui = new RewardsInfoGui(player, user, sessionToken);
                                    rewardsGui.openInventory();
                                })
                        )
                )
                .then(new LiteralArgument("set")
                        .withPermission("playtime.others.modify")
                        .then(customTargetArgument("target")
                                .then(new IntegerArgument("value", 1)
                                    .executes((sender, args) -> {
                                        String target = (String) args.get("target");
                                        int value = (Integer) args.get("value");
                                        if (target.equals("+")) {
                                            if (!sender.hasPermission("playtime.others.modify.all")) {
                                                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                                        " &cYou don't have permission to modify all players' join streaks."));
                                            } else {
                                                setAllPlayersJoinStreak(sender, value);
                                            }
                                        }else{
                                            DBUser user = dbUsersManager.getUserFromNickname(target);
                                            setPlayerJoinStreak(sender, user, value);
                                        }
                                    })
                                )
                        )
                )
                .register();
    }

    private void setPlayerJoinStreak(CommandSender sender, DBUser user, int newValue) {

        int oldStreakValue = user.getRelativeJoinStreak();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.setRelativeJoinStreak(newValue);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " Set join streak for player &e" + user.getNickname() +
                        "&7 from &e" + oldStreakValue + "&7 to &e" + newValue + "&7 joins"));
            });
        });
    }

    private void setAllPlayersJoinStreak(CommandSender sender, int newValue) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                " Starting to set all players' join streaks to &e" + newValue + "&7, this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            PlayTimeManager.getInstance().getDatabase().setRelativeJoinStreakForAll(newValue);
            OnlineUsersManager.getInstance().reload();

            dbUsersManager.clearCache();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " All players' join streaks have been set to &e" + newValue + "&7!"));
            });
        });
    }

}