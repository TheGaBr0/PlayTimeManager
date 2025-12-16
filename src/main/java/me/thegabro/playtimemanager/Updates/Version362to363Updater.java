package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version362to363Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public Version362to363Updater() {}

    public void performUpgrade() {
        new Version36to361Updater().performUpgrade(); /*issue in previous update system versions: if updating from prior
        3.6 versions to 3.6.2, the mid update to 3.6.1 is skipped. let's update again just to be sure.*/
        recreateConfigFile();
    }

    public void recreateConfigFile(){
        CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
        commandsConfig.initialize(plugin);

        commandsConfig.updateConfig();
        Configuration.getInstance().updateConfig(false);

    }

}
