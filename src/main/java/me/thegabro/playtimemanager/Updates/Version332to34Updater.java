package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Database.SQLiteDatabase;
import java.sql.*;

public class Version332to34Updater {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DatabaseHandler database = DatabaseHandler.getInstance();

    public Version332to34Updater() {}

    public void performUpgrade() {
        addRelativeJoinStreakColumn();
        addAbsoluteJoinStreakColumn();
        addReceivedRewardsColumn();
        addRewardsToBeClaimedColumn();
        recreateConfigFile();
    }

    public void addRelativeJoinStreakColumn() {

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the first_join column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN relative_join_streak INT DEFAULT 0;");

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

    public void addAbsoluteJoinStreakColumn() {

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the first_join column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN absolute_join_streak INT DEFAULT 0;");

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

    public void addReceivedRewardsColumn() {

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the received_rewards column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN received_rewards TEXT DEFAULT '';");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().severe("Failed to add received_rewards column: " + e.getMessage());
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error during received_rewards column addition: " + e.getMessage());
        }
    }

    public void addRewardsToBeClaimedColumn() {

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try (Statement s = connection.createStatement()) {
                // Alter the table to add the rewards_to_be_claimed column
                s.executeUpdate("ALTER TABLE play_time ADD COLUMN rewards_to_be_claimed TEXT DEFAULT '';");

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().severe("Failed to add rewards_to_be_claimed column: " + e.getMessage());
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error during rewards_to_be_claimed column addition: " + e.getMessage());
        }
    }

    private void recreateConfigFile() {
        Configuration.getInstance().updateConfig(true);
    }

}