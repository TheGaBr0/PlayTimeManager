package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.GUIs.JoinStreak.AllJoinStreakRewardsGui;
import me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayTimeJoinStreak implements CommandExecutor, TabCompleter {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.joinstreak")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
            return false;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Only players can use the GUI!"));
                return false;
            }
            AllJoinStreakRewardsGui gui = new AllJoinStreakRewardsGui();
            gui.openInventory((Player) sender);
            return true;
        }

        if(args.length == 1){
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Too few arguments!"));
            return false;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("seeplayer")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Only players can use this command!"));
                return false;
            }

            if (!player.hasPermission("playtime.joinstreak.seeplayer")) {
                player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cYou don't have permission to view other players' rewards."));
                return true;
            }

            String targetPlayerName = args[1];
            DBUser user = dbUsersManager.getUserFromNickname(targetPlayerName);

            if (user == null) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                        " The player &e" + targetPlayerName + "&7 has never joined the server!"));
                return true;
            }

            String sessionToken = UUID.randomUUID().toString();
            plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);

            RewardsInfoGui rewardsGui = new RewardsInfoGui(player, user, sessionToken);
            rewardsGui.openInventory();
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player) || !sender.hasPermission("playtime.joinstreak")) {
            return completions;
        }

        if (args.length == 1) {
            if (sender.hasPermission("playtime.joinstreak.seeplayer")) {
                completions.add("seeplayer");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("seeplayer") &&
                sender.hasPermission("playtime.joinstreak.seeplayer")) {
            String partialName = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partialName))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}