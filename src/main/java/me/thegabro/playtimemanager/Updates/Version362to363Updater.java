package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class Version362to363Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler database = DatabaseHandler.getInstance();

    public Version362to363Updater() {}

    public void performUpgrade() {
        new Version36to361Updater().performUpgrade(); /*issue in previous update system versions: if updating from prior
        3.6 versions to 3.6.2, the mid update to 3.6.1 is skipped. let's update again just to be sure.*/

        try {
            convertBIGINTtoTIMESTAMP();
            validateCompletedGoalsTimestamps();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        recreateConfigFile();
    }

    private void convertBIGINTtoTIMESTAMP() throws SQLException {

        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Check if columns are already TIMESTAMP type
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(play_time)");
                boolean needsUpdate = false;

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    String columnType = rs.getString("type");

                    if ((columnName.equals("last_seen") || columnName.equals("first_join"))
                            && columnType.toUpperCase().contains("BIGINT")) {
                        needsUpdate = true;
                        break;
                    }
                }
                rs.close();

                if (!needsUpdate) {
                    plugin.getLogger().info("  Schema already up to date, skipping migration");
                    conn.commit();
                    return;
                }

                plugin.getLogger().info("  Converting BIGINT timestamps to TIMESTAMP format...");

                stmt.execute(
                        "CREATE TABLE play_time_new (" +
                                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                                "nickname VARCHAR(36) NOT NULL UNIQUE, " +
                                "playtime BIGINT NOT NULL, " +
                                "artificial_playtime BIGINT NOT NULL, " +
                                "afk_playtime BIGINT NOT NULL, " +
                                "last_seen TIMESTAMP DEFAULT NULL, " +
                                "first_join TIMESTAMP DEFAULT NULL, " +
                                "relative_join_streak INT DEFAULT 0, " +
                                "absolute_join_streak INT DEFAULT 0, " +
                                "PRIMARY KEY (uuid))"
                );

                stmt.execute(
                        "INSERT INTO play_time_new " +
                                "(uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                                "last_seen, first_join, relative_join_streak, absolute_join_streak) " +
                                "SELECT uuid, nickname, playtime, artificial_playtime, afk_playtime, " +
                                "datetime(last_seen/1000, 'unixepoch'), " +  // Convert milliseconds to timestamp
                                "datetime(first_join/1000, 'unixepoch'), " +  // Convert milliseconds to timestamp
                                "relative_join_streak, absolute_join_streak " +
                                "FROM play_time"
                );

                stmt.execute("DROP TABLE play_time");
                stmt.execute("ALTER TABLE play_time_new RENAME TO play_time");

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void validateCompletedGoalsTimestamps() throws SQLException {

        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                plugin.getLogger().info("  Converting completed_goals millisecond timestamps to TIMESTAMP format...");

                // Update received_at values from milliseconds to timestamp format
                stmt.execute(
                        "UPDATE completed_goals " +
                                "SET received_at = datetime(CAST(received_at AS BIGINT)/1000, 'unixepoch') " +
                                "WHERE typeof(received_at) != 'text' OR received_at NOT LIKE '____-__-__ __:__:__'"
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