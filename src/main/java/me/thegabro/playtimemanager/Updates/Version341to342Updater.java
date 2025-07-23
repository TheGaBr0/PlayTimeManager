package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version341to342Updater {

    private final PlayTimeManager plugin;

    public Version341to342Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
    }

    public void performUpgrade() {
        recreateConfigFile();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }

}
