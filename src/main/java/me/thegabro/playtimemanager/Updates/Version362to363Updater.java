package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
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

        HashMap<String, Object> overridedFields = new HashMap<String, Object>();
        overridedFields.put("distribute-removed-time", true);


        playtimeFormatsConfiguration.formatsUpdater(overridedFields);
    }


    private void validateCompletedGoalsTimestamps() throws SQLException {

        try (Connection conn = DatabaseHandler.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                plugin.getLogger().info("  Converting completed_goals millisecond timestamps to ISO datetime format (UTC)...");

                // Update completed_at values from milliseconds to ISO datetime format (UTC)
                stmt.execute(
                        "UPDATE completed_goals " +
                                "SET completed_at = strftime('%Y-%m-%d %H:%M:%S', CAST(completed_at AS BIGINT)/1000, 'unixepoch') " +
                                "WHERE typeof(completed_at) != 'text' OR completed_at NOT LIKE '____-__-__ __:__:__'"
                );

                // Update received_at values from milliseconds to ISO datetime format (UTC)
                // Only update non-NULL values
                stmt.execute(
                        "UPDATE completed_goals " +
                                "SET received_at = strftime('%Y-%m-%d %H:%M:%S', CAST(received_at AS BIGINT)/1000, 'unixepoch') " +
                                "WHERE received_at IS NOT NULL " +
                                "AND (typeof(received_at) != 'text' OR received_at NOT LIKE '____-__-__ __:__:__')"
                );

                conn.commit();
                plugin.getLogger().info("  ✓ Completed goals timestamps converted successfully to UTC ISO format");
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().severe("  ✗ Failed to convert completed goals timestamps: " + e.getMessage());
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