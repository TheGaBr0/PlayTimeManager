package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class Version362to363Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public Version362to363Updater() {}

    public void performUpgrade() {
        new Version36to361Updater().performUpgrade(); /*issue in previous update system versions: if updating from prior
        3.6 versions to 3.6.2, the mid-update to 3.6.1 is skipped. let's update again just to be sure.*/

        try {
            validateCompletedGoalsTimestamps();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        PlaytimeFormatsConfiguration playtimeFormatsConfiguration = PlaytimeFormatsConfiguration.getInstance();
        playtimeFormatsConfiguration.initialize(plugin);

        updatePlaytimeFormatsData(playtimeFormatsConfiguration);

        recreateConfigFile();
    }

    public void updatePlaytimeFormatsData(PlaytimeFormatsConfiguration playtimeFormatsConfiguration){
        playtimeFormatsConfiguration.formatsUpdater();
    }


    private void validateCompletedGoalsTimestamps() throws SQLException {

        try (Connection conn = DatabaseHandler.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                plugin.getLogger().info("  Converting completed_goals millisecond timestamps to TEXT datetime format...");

                // Update completed_at values from milliseconds to TEXT datetime format
                stmt.execute(
                        "UPDATE completed_goals " +
                                "SET completed_at = datetime(CAST(completed_at AS BIGINT)/1000, 'unixepoch') " +
                                "WHERE typeof(completed_at) != 'text' OR completed_at NOT LIKE '____-__-__ __:__:__'"
                );

                // Update received_at values from milliseconds to TEXT datetime format
                // Only update non-NULL values
                stmt.execute(
                        "UPDATE completed_goals " +
                                "SET received_at = datetime(CAST(received_at AS BIGINT)/1000, 'unixepoch') " +
                                "WHERE received_at IS NOT NULL " +
                                "AND (typeof(received_at) != 'text' OR received_at NOT LIKE '____-__-__ __:__:__')"
                );

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void recreateConfigFile(){
        CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
        commandsConfig.initialize(plugin);

        commandsConfig.updateConfig();
        Configuration.getInstance().updateConfig(false);
    }
}