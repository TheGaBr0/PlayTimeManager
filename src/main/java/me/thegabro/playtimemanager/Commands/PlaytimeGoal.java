package me.thegabro.playtimemanager.Commands;

import me.thegabro.playtimemanager.GUIs.Goals.AllGoalsGui;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlaytimeGoal implements TabExecutor {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final String[] SUBCOMMANDS = {"create", "remove", "rename"};  // Added "create" subcommand
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {

        if (!sender.hasPermission("playtime.goal")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " You don't have the permission to execute this command"));
            return false;
        }

        // If no arguments provided and sender is a player, open GUI
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Only players can use the GUI!"));
                return false;
            }
            AllGoalsGui gui = new AllGoalsGui();
            gui.openInventory((Player) sender);
            return true;
        }

        String goalName;
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Usage: /playtimegoal create <goalName>"));
                    return false;
                }
                goalName = args[1];
                createGoal(sender, goalName);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Usage: /playtimegoal remove <goalName>"));
                    return false;
                }
                goalName = args[1];
                removeGoal(sender, goalName);
                break;
            case "rename":
                if (args.length != 3) {
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Usage: /playtimegoal rename <oldName> <newName>"));
                    return false;
                }
                String oldName = args[1];
                String newName = args[2];
                renameGoal(sender, oldName, newName);
                break;
            default:
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Subcommand " + subCommand + " is not valid."));
                return false;
        }

        return true;
    }

    private void createGoal(CommandSender sender, String goalName) {
        // Check if goal already exists
        if (goalsManager.getGoal(goalName) != null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " A goal with the name &e" + goalName + " &7already exists!"));
            return;
        }

        // Check if goal name is empty or invalid
        if (goalName.trim().isEmpty()) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Goal name cannot be empty!"));
            return;
        }

        // Run the creation process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Create new goal with inactive status (false)
            new Goal(plugin, goalName, false);

            // Switch back to main thread for UI update
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Goal &e" + goalName + " has been created &asuccessfully &7(inactive by default)." +
                        " &7To edit this goal, use the GUI or manually modify the &e" + goalName + ".yml &7file."));
            });
        });
    }

    private void renameGoal(CommandSender sender, String oldName, String newName) {
        Goal oldGoal = goalsManager.getGoal(oldName);
        if (oldGoal == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " The goal &e" + oldName + " &7doesn't exist!"));
            return;
        }

        if (goalsManager.getGoal(newName) != null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " A goal with the name &e" + newName + " &7already exists!"));
            return;
        }

        // Run the rename process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            oldGoal.rename(newName);

            // Switch back to main thread for UI update
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " Successfully renamed goal &e" + oldName + " &7to &e" + newName));
            });
        });
    }


    private void removeGoal(CommandSender sender, String goalName) {
        Goal goal = goalsManager.getGoal(goalName);
        if (goal == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " The goal &e" + goalName + " &7doesn't exist!"));
            return;
        }

        // Run the removal process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            goal.kill();

            // Switch back to main thread for UI updates and schedule changes
            Bukkit.getScheduler().runTask(plugin, () -> {
                onlineUsersManager.startGoalCheckSchedule();
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " The goal &e" + goalName + " &7has been removed!"));
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
            if(args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("rename")) {
                StringUtil.copyPartialMatches(args[1], goalsManager.getGoalsNames(), completions);
            }
            return completions;
        }


        return null;
    }
}