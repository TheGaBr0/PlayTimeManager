package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;

public class Version36to361Updater {
    public Version36to361Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }
}
