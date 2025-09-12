package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version354to355Updater {

    private final PlayTimeManager plugin;
    private final GUIsConfiguration guIsConfiguration = GUIsConfiguration.getInstance();
    public Version354to355Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void performUpgrade() {
        recreateConfigFile();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(false);
        guIsConfiguration.initialize(plugin);
        guIsConfiguration.updateConfig();
    }

}
