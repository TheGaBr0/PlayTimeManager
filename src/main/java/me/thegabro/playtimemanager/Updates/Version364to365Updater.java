package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;

public class Version364to365Updater {

    public Version364to365Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }

}
