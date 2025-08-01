package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class Version31to32Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;

    public Version31to32Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        handleDuplicates();
        updateUniqueDBEntries();
        addLastSeenColumn();
        recreateConfigFile();
    }

    private String mergeGoals(String goals) {
        if (goals == null || goals.isEmpty()) {
            return "";
        }

        Set<String> uniqueGoals = new LinkedHashSet<>();
        for (String goalList : goals.split(",")) {
            String goal = goalList.trim();
            if (!goal.isEmpty()) {
                uniqueGoals.add(goal);
            }
        }

        return String.join(",", uniqueGoals);
    }

    private void handleDuplicates() {
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                s.executeUpdate("CREATE TEMPORARY TABLE temp_merged_entries (" +
                        "uuid VARCHAR(32) NOT NULL," +
                        "nickname VARCHAR(32) NOT NULL," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''" +
                        ")");
                s.executeUpdate("CREATE INDEX idx_temp_uuid ON temp_merged_entries(uuid)");

                ResultSet rs = s.executeQuery(
                        "SELECT uuid, " +
                                "MAX(nickname) as nickname, " +
                                "SUM(playtime) as total_playtime, " +
                                "SUM(artificial_playtime) as total_artificial_playtime, " +
                                "GROUP_CONCAT(completed_goals) as all_goals " +
                                "FROM play_time GROUP BY uuid"
                );

                PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO temp_merged_entries (uuid, nickname, playtime, artificial_playtime, completed_goals) " +
                                "VALUES (?, ?, ?, ?, ?)"
                );

                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String nickname = rs.getString("nickname");
                    long playtime = rs.getLong("total_playtime");
                    long artificialPlaytime = rs.getLong("total_artificial_playtime");
                    String allGoals = rs.getString("all_goals");

                    String mergedGoals = mergeGoals(allGoals);

                    insertStmt.setString(1, uuid);
                    insertStmt.setString(2, nickname);
                    insertStmt.setLong(3, playtime);
                    insertStmt.setLong(4, artificialPlaytime);
                    insertStmt.setString(5, mergedGoals);
                    insertStmt.executeUpdate();
                }
                rs.close();
                insertStmt.close();

                s.executeUpdate("CREATE TABLE final_merged_entries (" +
                        "uuid VARCHAR(32) NOT NULL," +
                        "nickname VARCHAR(32) NOT NULL," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''" +
                        ")");

                rs = s.executeQuery(
                        "SELECT MAX(uuid) as uuid, " +
                                "nickname, " +
                                "SUM(playtime) as total_playtime, " +
                                "SUM(artificial_playtime) as total_artificial_playtime, " +
                                "GROUP_CONCAT(completed_goals) as all_goals " +
                                "FROM temp_merged_entries GROUP BY nickname"
                );

                insertStmt = connection.prepareStatement(
                        "INSERT INTO final_merged_entries (uuid, nickname, playtime, artificial_playtime, completed_goals) " +
                                "VALUES (?, ?, ?, ?, ?)"
                );

                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String nickname = rs.getString("nickname");
                    long playtime = rs.getLong("total_playtime");
                    long artificialPlaytime = rs.getLong("total_artificial_playtime");
                    String allGoals = rs.getString("all_goals");

                    String mergedGoals = mergeGoals(allGoals);

                    insertStmt.setString(1, uuid);
                    insertStmt.setString(2, nickname);
                    insertStmt.setLong(3, playtime);
                    insertStmt.setLong(4, artificialPlaytime);
                    insertStmt.setString(5, mergedGoals);
                    insertStmt.executeUpdate();
                }

                ResultSet countRs = s.executeQuery("SELECT COUNT(*) as total FROM play_time");
                int originalCount = countRs.next() ? countRs.getInt("total") : 0;
                countRs.close();

                countRs = s.executeQuery("SELECT COUNT(*) as total FROM final_merged_entries");
                int finalCount = countRs.next() ? countRs.getInt("total") : 0;
                countRs.close();

                plugin.getLogger().info(String.format("Merged %d entries into %d unique entries",
                        originalCount, finalCount));

                s.executeUpdate("DELETE FROM play_time");
                s.executeUpdate("INSERT INTO play_time SELECT * FROM final_merged_entries");

                s.executeUpdate("DROP TABLE IF EXISTS temp_merged_entries");
                s.executeUpdate("DROP TABLE IF EXISTS final_merged_entries");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to handle duplicates: " + e.getMessage(), e);
        }
    }

    public void updateUniqueDBEntries() {
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {

                // Create new table with indexes
                s.executeUpdate("CREATE TABLE play_time_new (" +
                        "uuid VARCHAR(32) NOT NULL UNIQUE," +
                        "nickname VARCHAR(32) NOT NULL UNIQUE," +
                        "playtime BIGINT NOT NULL," +
                        "artificial_playtime BIGINT NOT NULL," +
                        "completed_goals TEXT DEFAULT ''," +
                        "PRIMARY KEY (uuid)" +
                        ")");

                // Copy data efficiently
                s.executeUpdate("INSERT INTO play_time_new SELECT * FROM play_time");
                s.executeUpdate("DROP TABLE play_time");
                s.executeUpdate("ALTER TABLE play_time_new RENAME TO play_time");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add unique constraints: " + e.getMessage());
        }
    }

    public void addLastSeenColumn() {
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the last_seen column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN last_seen DATETIME DEFAULT NULL");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to alter table: " + e.getMessage());
        }
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }


}