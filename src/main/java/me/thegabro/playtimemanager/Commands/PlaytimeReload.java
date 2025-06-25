package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import me.thegabro.playtimemanager.Utils;


public class PlaytimeReload implements CommandRegistrar {
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final JoinStreaksManager joinStreaksManager = JoinStreaksManager.getInstance();

    public void registerCommands(){
        new CommandAPICommand("playtimereload")
                .withAliases("ptreload")
                .withPermission(CommandPermission.fromString("playtime.reload"))
                .executes((sender, args) -> {
                    // Reload configurations
                    plugin.getConfiguration().reload();
                    plugin.getGUIsConfig().reload();
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " The configuration files have been reloaded"));

                    // Reload goals
                    goalsManager.clearGoals();
                    goalsManager.loadGoals();

                    //reload online users data
                    onlineUsersManager.reload();


                    // Restart LuckPerms schedule if applicable
                    onlineUsersManager.startGoalCheckSchedule();
                    sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Goal check schedule has been restarted"));

                    dbUsersManager.updateTopPlayersFromDB();

                    joinStreaksManager.getRewardRegistry().clearRewards();
                    joinStreaksManager.getRewardRegistry().loadRewards();

                    joinStreaksManager.getCycleScheduler().initialize();

                    // Only start the task if it's enabled in config
                    if (plugin.getConfiguration().getRewardsCheckScheduleActivation()) {
                        joinStreaksManager.getCycleScheduler().startIntervalTask();
                        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " Join streak check schedule has been restarted"));
                    }
                })
                .register();
    }
}