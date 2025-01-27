package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import me.thegabro.playtimemanager.Users.DBUsersManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public class Version31to311Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;
    private final DBUsersManager dbUsersManager;

    public Version31to311Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
        this.dbUsersManager = DBUsersManager.getInstance();
    }

    public void performUpgrade() {
        handleDuplicates();
        updateUniqueDBEntries();
        updateConfig();
    }

    private void handleDuplicates() {
        try (Connection connection = database.getSQLConnection()) {
            // Create backup before any modifications
            DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
            backupUtility.createBackup("play_time", "Backup before 3.1.1 duplicate handling");

            try (Statement s = connection.createStatement()) {
                // Create function for merging goals
                s.executeUpdate("CREATE TEMPORARY TABLE IF NOT EXISTS temp_merged_entries (" +
                        "uuid VARCHAR(32) NOT NULL," +
                        "nickname VARCHAR(32) NOT NULL," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''" +
                        ")");

                // Process records with duplicate UUIDs
                try (ResultSet rs = s.executeQuery("SELECT uuid, GROUP_CONCAT(nickname) as nicknames, " +
                        "SUM(playtime) as total_playtime, " +
                        "SUM(artificial_playtime) as total_artificial_playtime, " +
                        "GROUP_CONCAT(completed_goals) as all_goals " +
                        "FROM play_time GROUP BY uuid")) {

                    while (rs.next()) {
                        String uuid = rs.getString("uuid");
                        String[] nicknames = rs.getString("nicknames").split(",");
                        long totalPlaytime = rs.getLong("total_playtime");
                        long totalArtificialPlaytime = rs.getLong("total_artificial_playtime");
                        String[] allGoals = rs.getString("all_goals").split(",");

                        // Use the most recent nickname (last in the list)
                        String finalNickname = nicknames[nicknames.length - 1];

                        // Merge goals ensuring uniqueness
                        String mergedGoals = String.join(",", new LinkedHashSet<>(Arrays.asList(allGoals)));

                        // Insert merged record
                        try (Statement insertStmt = connection.createStatement()) {
                            insertStmt.executeUpdate(String.format(
                                    "INSERT INTO temp_merged_entries VALUES ('%s', '%s', %d, %d, '%s')",
                                    uuid, finalNickname, totalPlaytime, totalArtificialPlaytime, mergedGoals));
                        }
                    }
                }

                // Process records with duplicate nicknames
                s.executeUpdate("CREATE TABLE IF NOT EXISTS final_merged_entries (" +
                        "uuid VARCHAR(32) NOT NULL," +
                        "nickname VARCHAR(32) NOT NULL," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''" +
                        ")");

                try (ResultSet rs = s.executeQuery("SELECT nickname, GROUP_CONCAT(uuid) as uuids, " +
                        "SUM(playtime) as total_playtime, " +
                        "SUM(artificial_playtime) as total_artificial_playtime, " +
                        "GROUP_CONCAT(completed_goals) as all_goals " +
                        "FROM temp_merged_entries GROUP BY nickname")) {

                    while (rs.next()) {
                        String nickname = rs.getString("nickname");
                        String[] uuids = rs.getString("uuids").split(",");
                        long totalPlaytime = rs.getLong("total_playtime");
                        long totalArtificialPlaytime = rs.getLong("total_artificial_playtime");
                        String[] allGoals = rs.getString("all_goals").split(",");

                        // Use the most recent UUID (last in the list)
                        String finalUuid = uuids[uuids.length - 1];

                        // Merge goals ensuring uniqueness
                        String mergedGoals = String.join(",", new LinkedHashSet<>(Arrays.asList(allGoals)));

                        // Insert final merged record
                        try (Statement insertStmt = connection.createStatement()) {
                            insertStmt.executeUpdate(String.format(
                                    "INSERT INTO final_merged_entries VALUES ('%s', '%s', %d, %d, '%s')",
                                    finalUuid, nickname, totalPlaytime, totalArtificialPlaytime, mergedGoals));
                        }
                    }
                }

                // Log the number of affected entries
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*) as total FROM play_time")) {
                    int originalCount = rs.next() ? rs.getInt("total") : 0;
                    try (ResultSet rs2 = s.executeQuery("SELECT COUNT(*) as total FROM final_merged_entries")) {
                        int finalCount = rs2.next() ? rs2.getInt("total") : 0;
                        plugin.getLogger().info(String.format("Merged %d entries into %d unique entries",
                                originalCount, finalCount));
                    }
                }

                // Clear original table and insert merged data
                s.executeUpdate("DELETE FROM play_time");
                s.executeUpdate("INSERT INTO play_time SELECT * FROM final_merged_entries");

                // Clean up temporary tables
                s.executeUpdate("DROP TABLE IF EXISTS temp_merged_entries");
                s.executeUpdate("DROP TABLE IF EXISTS final_merged_entries");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to handle duplicates: " + e.getMessage(), e);
        }
    }

    public void updateUniqueDBEntries() {
        try (Connection connection = database.getSQLConnection()) {
            // Create backup before constraint addition
            DatabaseBackupUtility backupUtility = new DatabaseBackupUtility(plugin);
            backupUtility.createBackup("play_time", "Backup before 3.1.1 unique constraint addition");

            // Rename existing table
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE play_time RENAME TO play_time_old");

                // Create new table with unique constraints
                s.executeUpdate("CREATE TABLE play_time (" +
                        "uuid VARCHAR(32) NOT NULL UNIQUE," +
                        "nickname VARCHAR(32) NOT NULL UNIQUE," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''," +
                        "PRIMARY KEY (uuid)" +
                        ")");

                // Copy data to new table
                s.executeUpdate("INSERT INTO play_time SELECT * FROM play_time_old");

                // Drop old table
                s.executeUpdate("DROP TABLE play_time_old");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add unique constraints: " + e.getMessage());
        }
    }

    public void updateConfig() {
        Configuration config = plugin.getConfiguration();
        config.setVersion((float) 3.3);
    }
}