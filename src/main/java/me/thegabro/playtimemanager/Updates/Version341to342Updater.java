package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;

public class Version341to342Updater {

    public Version341to342Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }

}
