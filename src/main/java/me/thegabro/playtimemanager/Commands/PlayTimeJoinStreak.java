package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.GUIs.JoinStreak.AllJoinStreakRewardsGui;
import me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlayTimeJoinStreak implements CommandExecutor, TabCompleter {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.joinstreak")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have the permission to execute this command"));
            return false;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Only players can use the GUI!"));
                return false;
            }
            AllJoinStreakRewardsGui gui = new AllJoinStreakRewardsGui();
            gui.openInventory((Player) sender);
            return true;
        }

        if(args.length == 1){
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Too few arguments!"));
            return false;
        }

        if (args[0].equalsIgnoreCase("seeplayer")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Only players can use this command!"));
                return false;
            }

            if (!player.hasPermission("playtime.joinstreak.seeplayer")) {
                player.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " &cYou don't have permission to view other players' rewards."));
                return true;
            }

            String targetPlayerName = args[1];
            DBUser user = dbUsersManager.getUserFromNickname(targetPlayerName);

            if (user == null) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " The player &e" + targetPlayerName + "&7 has never joined the server!"));
                return true;
            }

            String sessionToken = UUID.randomUUID().toString();
            plugin.getSessionManager().createSession(player.getUniqueId(), sessionToken);

            RewardsInfoGui rewardsGui = new RewardsInfoGui(player, user, sessionToken);
            rewardsGui.openInventory();
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            String targetPlayerName = args[1];
            String valueString = args[2];

            if (targetPlayerName.equals("*")) {
                if (!sender.hasPermission("playtime.others.modify.all")) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                            " &cYou don't have permission to modify all players' join streaks."));
                    return true;
                }
            } else {
                if (!sender.hasPermission("playtime.others.modify")) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                            " &cYou don't have permission to modify other players' join streaks."));
                    return true;
                }
            }

            int newStreakValue;
            try {
                newStreakValue = Integer.parseInt(valueString);
                if (newStreakValue < 0) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                            " &cJoin streak value must be 0 or greater!"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " &cInvalid number: &e" + valueString + "&c. Please enter a valid integer."));
                return true;
            }

            if (targetPlayerName.equals("*")) {
                setAllPlayersJoinStreak(sender, newStreakValue);
                return true;
            }

            setPlayerJoinStreak(sender, targetPlayerName, newStreakValue);
            return true;
        }

        return false;
    }

    private void setPlayerJoinStreak(CommandSender sender, String playerName, int newValue) {
        DBUser user = dbUsersManager.getUserFromNickname(playerName);

        if (user == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                    " The player &e" + playerName + "&7 has never joined the server!"));
            return;
        }

        int oldStreakValue = user.getRelativeJoinStreak();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            user.setRelativeJoinStreak(newValue);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " Set join streak for player &e" + playerName +
                        "&7 from &e" + oldStreakValue + "&7 to &e" + newValue + "&7 joins"));
            });
        });
    }

    private void setAllPlayersJoinStreak(CommandSender sender, int newValue) {
        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                " Starting to set all players' join streaks to &e" + newValue + "&7, this will take some time..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<DBUser> users = dbUsersManager.getAllDBUsers();
            AtomicInteger totalPlayersModified = new AtomicInteger();

            for (DBUser u : users) {
                u.setRelativeJoinStreak(newValue);
                totalPlayersModified.getAndIncrement();
            }

            dbUsersManager.clearCaches();

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") +
                        " All players' join streaks have been set to &e" + newValue + "&7! Total: &e" + totalPlayersModified + "&7 players modified"));
            });
        });
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
            if (sender.hasPermission("playtime.others.modify")) {
                completions.add("set");
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("seeplayer") &&
                    sender.hasPermission("playtime.joinstreak.seeplayer")) {
                String partialName = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partialName))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("set") &&
                    sender.hasPermission("playtime.others.modify")) {
                String partialName = args[1].toLowerCase();
                List<String> playerCompletions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partialName))
                        .collect(Collectors.toList());

                // Add wildcard option if user has the all permission
                if (sender.hasPermission("playtime.others.modify.all") && "*".startsWith(partialName)) {
                    playerCompletions.add("*");
                }

                return playerCompletions;
            }
        }


        return completions;
    }
}