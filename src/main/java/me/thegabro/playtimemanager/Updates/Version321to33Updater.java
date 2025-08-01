package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.SQLiteDB.SQLite;
import java.sql.*;


public class Version321to33Updater {
    private final PlayTimeManager plugin;
    private final SQLite database;

    public Version321to33Updater(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.database = (SQLite) plugin.getDatabase();
    }

    public void performUpgrade() {
        addFirstJoinColumn();
        recreateConfigFile();
    }

    public void addFirstJoinColumn() {
        try (Connection connection = database.getSQLConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the first_join column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN first_join DATETIME DEFAULT NULL");

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