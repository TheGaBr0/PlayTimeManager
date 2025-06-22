package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import me.thegabro.playtimemanager.GUIs.Goals.AllGoalsGui;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

public class PlaytimeGoal implements CommandRegistrar {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();


    public void registerCommands(){

        new CommandTree("playtimegoal")
                .withPermission(CommandPermission.fromString("playtime.goal"))
                .executesConsole((console, args) -> {
                    console.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " You must be a player to execute this command."));
                })
                .executesPlayer((player, args) -> {
                    AllGoalsGui gui = new AllGoalsGui();
                    gui.openInventory(player);
                })
                .then(new LiteralArgument("create")
                        .then(new StringArgument("goalName")
                            .executes((sender, args) -> {
                                String goalName = (String) args.get("goalName");
                                createGoal(sender, goalName);
                            })
                        )
                )
                .then(new LiteralArgument("remove")
                        .then(new StringArgument("goalName").replaceSuggestions(
                                ArgumentSuggestions.stringCollection(info -> goalsManager.getGoalsNames()))
                                .executes((sender, args) -> {
                                    String goalName = (String) args.get("goalName");
                                    removeGoal(sender, goalName);
                                })
                        )
                )
                .then(new LiteralArgument("rename")
                        .then(new StringArgument("oldGoalName").replaceSuggestions(
                                ArgumentSuggestions.stringCollection(info -> goalsManager.getGoalsNames()))
                                .then(new StringArgument("newGoalName")
                                    .executes((sender, args) -> {
                                        String oldGoalName = (String) args.get("oldGoalName");
                                        String newGoalName = (String) args.get("newGoalName");
                                        renameGoal(sender, oldGoalName, newGoalName);
                                    })
                                )
                        )
                ).register();
    }

    private void createGoal(CommandSender sender, String goalName) {
        // Check if goal already exists
        if (goalsManager.getGoal(goalName) != null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " A goal with the name &e" + goalName + " &7already exists!"));
            return;
        }

        // Check if goal name is empty or invalid
        if (goalName.trim().isEmpty()) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Goal name cannot be empty!"));
            return;
        }

        // Run the creation process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Create new goal with inactive status (false)
            new Goal(plugin, goalName, false);

            // Switch back to main thread for UI update
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Goal &e" + goalName + " has been created &asuccessfully &7(inactive by default)." +
                        " &7To edit this goal, use the GUI or manually modify the &e" + goalName + ".yml &7file."));
            });
        });
    }

    private void renameGoal(CommandSender sender, String oldName, String newName) {
        Goal oldGoal = goalsManager.getGoal(oldName);
        if (oldGoal == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The goal &e" + oldName + " &7doesn't exist!"));
            return;
        }

        if (goalsManager.getGoal(newName) != null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " A goal with the name &e" + newName + " &7already exists!"));
            return;
        }

        // Run the rename process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            oldGoal.rename(newName);

            // Switch back to main thread for UI update
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Successfully renamed goal &e" + oldName + " &7to &e" + newName));
            });
        });
    }


    private void removeGoal(CommandSender sender, String goalName) {
        Goal goal = goalsManager.getGoal(goalName);
        if (goal == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The goal &e" + goalName + " &7doesn't exist!"));
            return;
        }

        // Run the removal process async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            goal.kill();

            // Switch back to main thread for UI updates and schedule changes
            Bukkit.getScheduler().runTask(plugin, () -> {
                onlineUsersManager.startGoalCheckSchedule();
                sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The goal &e" + goalName + " &7has been removed!"));
            });
        });
    }

}