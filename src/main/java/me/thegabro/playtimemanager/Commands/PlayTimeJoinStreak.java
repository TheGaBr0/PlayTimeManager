package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.GUIs.JoinStreak.AllJoinStreakRewardsGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayTimeJoinStreak implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final String[] SUBCOMMANDS = {"create", "remove"};
    private final String[] SUBSUBCOMMANDS = {"joins:", "activate:"};
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.joinstreak")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You don't have the permission to execute this command"));
            return false;
        }

        // If no arguments provided and sender is a player, open GUI
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Only players can use the GUI!"));
                return false;
            }
            AllJoinStreakRewardsGui gui = new AllJoinStreakRewardsGui();
            gui.openInventory((Player) sender);
            return true;
        }
        int rewardID;
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Usage: /playtimejoinstreak create <rewardName> [joins:<joins>] [activate:true|false]"));
                    return false;
                }

                rewardID = Integer.parseInt(args[1]);
                String joinsStr = null;
                boolean activate = false;

                // Process optional arguments
                for (int i = 2; i < args.length; i++) {
                    if (args[i].startsWith("joins:")) {
                        joinsStr = args[i].substring(6);
                        if (joinsStr.isEmpty()) {
                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Missing joins value!"));
                            return false;
                        }
                        try {
                            Long.parseLong(joinsStr);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid joins format! Must be a number."));
                            return false;
                        }
                    } else if (args[i].startsWith("activate:")) {
                        String activateValue = args[i].substring(9).toLowerCase();
                        if (activateValue.equals("true")) {
                            activate = true;
                        } else if (!activateValue.equals("false")) {
                            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid activate value! Use true or false"));
                            return false;
                        }
                    } else {
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Invalid argument: " + args[i]));
                        return false;
                    }
                }

                createReward(sender, rewardID, joinsStr != null ? Integer.parseInt(joinsStr) : null, activate);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Usage: /playtimejoinstreak remove <rewardName>"));
                    return false;
                }
                rewardID = Integer.parseInt(args[1]);
                removeReward(sender, rewardID);
                break;
            default:
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Subcommand " + subCommand + " is not valid."));
                return false;
        }

        return true;
    }

    private void createReward(CommandSender sender, int rewardID, int requiredJoins, boolean activate) {
        JoinStreakReward reward = joinStreaksManager.getReward(rewardID);
        StringBuilder message = new StringBuilder();

        if (reward != null) {
            message.append(plugin.getConfiguration().getPluginPrefix()).append(" Join Streak Reward &e").append(rewardID).append(" &7updated:\n");
            reward.setRequiredJoins(requiredJoins);
        } else {
            reward = new JoinStreakReward(plugin, rewardID, requiredJoins);
            message.append(plugin.getConfiguration().getPluginPrefix()).append(" Join Streak Reward &e").append(rewardID).append(" &7created:\n");
        }

        long requiredJoinsValue = reward.getRequiredJoins();
        if(requiredJoinsValue == -1)
            message.append("&7- Required joins to receive the reward: &6None\n");
        else
            message.append("&7- Required joins to receive the reward: &6").append(requiredJoinsValue).append("\n");
        message.append("&7- Active: ").append(activate ? "&a" : "&c").append(activate).append("\n");

        sender.sendMessage(Utils.parseColors(message.toString()));
    }

    private void removeReward(CommandSender sender, int rewardID) {
        JoinStreakReward reward = joinStreaksManager.getReward(rewardID);
        if (reward == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The reward &e" + rewardID + " &7doesn't exist!"));
            return;
        }

        // Run the removal process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            reward.kill();

            // Switch back to main thread for UI updates
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The reward &e" + rewardID + " &7has been removed!"));
            });
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUBCOMMANDS), completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                //StringUtil.copyPartialMatches(args[1], joinStreaksManager.getRewardNames(), completions);
            } else if (args[0].equalsIgnoreCase("remove")) {
                //StringUtil.copyPartialMatches(args[1], joinStreaksManager.getRewardNames(), completions);
            }
            return completions;
        }

        if (args.length >= 3) {
            if (args[0].equalsIgnoreCase("create")) {
                List<String> availableSubSubCommands = new ArrayList<>(Arrays.asList(SUBSUBCOMMANDS));

                // Remove already used arguments
                for (int i = 2; i < args.length - 1; i++) {
                    for (String subSubCmd : SUBSUBCOMMANDS) {
                        if (args[i].startsWith(subSubCmd)) {
                            availableSubSubCommands.remove(subSubCmd);
                        }
                    }
                }

                StringUtil.copyPartialMatches(args[args.length - 1], availableSubSubCommands, completions);
                return completions;
            }
        }

        return null;
    }
}