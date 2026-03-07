package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardUpdater;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version363to364Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public Version363to364Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
        updateJoinStreakRewards();
    }

    public void updateJoinStreakRewards(){
        RewardUpdater.getInstance().rewardsUpdater();

        RewardRegistry.getInstance().loadRewards();
        for(JoinStreakReward j : RewardRegistry.getInstance().getRewards()){
            plugin.getLogger().info(String.valueOf(j.isRepeatable()));
            j.setRepeatable(true);
            plugin.getLogger().info(String.valueOf(j.isRepeatable()));
        }

    }

    public void recreateConfigFile(){
        CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
        commandsConfig.initialize(plugin);

        Configuration.getInstance().updateConfig(false);
    }

}
