package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;

public class Version365to366Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();

    public Version365to366Updater() {}

    public void performUpgrade() {
        updateGoalData();
        recreateConfigFile();
    }

    /**
     * Rewrites every goal's .yml file so it gains the new per-player-check key
     * (and updated header comments) while preserving existing customizations.
     */
    private void updateGoalData() {
        // Goal loading calls translateCheckTimeToText(), which needs the playtime
        // format loaded to format the check interval - must run first.
        PlaytimeFormatsConfiguration.getInstance().initialize(plugin);

        goalsManager.initialize(plugin);
        goalsManager.goalsUpdater();

        GoalsManager.resetInstance();
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(false);
    }
}
