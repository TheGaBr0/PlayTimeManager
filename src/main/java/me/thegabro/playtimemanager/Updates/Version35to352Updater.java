package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version35to352Updater {

    private PlayTimeManager plugin;
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();

    public Version35to352Updater(PlayTimeManager plugin){
        this.plugin = plugin;
    }

    public void performUpgrade() {
        recreateConfigFile();
    }

    public void recreateConfigFile(){
        guIsConfiguration.initialize(plugin);
        guIsConfiguration.updateConfig();
        plugin.getConfiguration().updateConfig(false);
    }


}
