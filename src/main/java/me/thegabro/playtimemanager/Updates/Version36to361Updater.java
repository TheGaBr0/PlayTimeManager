package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.*;
import java.util.logging.Level;

public class Version36to361Updater {
    public Version36to361Updater() {}

    public void performUpgrade() {
        fix_tables();
        recreateConfigFile();
    }

    public void recreateConfigFile(){
        Configuration.getInstance().updateConfig(false);
    }

    private void fix_tables() {
        DatabaseHandler db = DatabaseHandler.getInstance();

        try (Connection conn = db.getConnection()) {
            // Fix completed_goals table
            fixCompletedGoals(conn);

            // Fix received_rewards table
            fixReceivedRewards(conn);

            // Fix rewards_to_be_claimed table
            fixRewardsToBeClaimed(conn);

            PlayTimeManager.getInstance().getLogger().info("Successfully fixed inconsistent nicknames in database tables");
        } catch (SQLException e) {
            PlayTimeManager.getInstance().getLogger().log(Level.SEVERE, "Failed to fix database tables", e);
        }
    }

    private void fixCompletedGoals(Connection conn) throws SQLException {
        String findDuplicatesQuery =
                "SELECT user_uuid, nickname, MAX(completed_at) as latest_completion " +
                        "FROM completed_goals " +
                        "GROUP BY user_uuid " +
                        "HAVING COUNT(DISTINCT nickname) > 1";

        String updateQuery =
                "UPDATE completed_goals " +
                        "SET nickname = ? " +
                        "WHERE user_uuid = ? AND nickname != ?";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findDuplicatesQuery);
             PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

            while (rs.next()) {
                String uuid = rs.getString("user_uuid");

                // Get the correct nickname from the latest record
                String getLatestNicknameQuery =
                        "SELECT nickname FROM completed_goals " +
                                "WHERE user_uuid = ? " +
                                "ORDER BY completed_at DESC LIMIT 1";

                try (PreparedStatement latestStmt = conn.prepareStatement(getLatestNicknameQuery)) {
                    latestStmt.setString(1, uuid);
                    try (ResultSet latestRs = latestStmt.executeQuery()) {
                        if (latestRs.next()) {
                            String correctNickname = latestRs.getString("nickname");

                            updateStmt.setString(1, correctNickname);
                            updateStmt.setString(2, uuid);
                            updateStmt.setString(3, correctNickname);
                            updateStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private void fixReceivedRewards(Connection conn) throws SQLException {
        String findDuplicatesQuery =
                "SELECT user_uuid, nickname, MAX(received_at) as latest_receipt " +
                        "FROM received_rewards " +
                        "GROUP BY user_uuid " +
                        "HAVING COUNT(DISTINCT nickname) > 1";

        String updateQuery =
                "UPDATE received_rewards " +
                        "SET nickname = ? " +
                        "WHERE user_uuid = ? AND nickname != ?";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findDuplicatesQuery);
             PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

            while (rs.next()) {
                String uuid = rs.getString("user_uuid");

                // Get the correct nickname from the latest record
                String getLatestNicknameQuery =
                        "SELECT nickname FROM received_rewards " +
                                "WHERE user_uuid = ? " +
                                "ORDER BY received_at DESC LIMIT 1";

                try (PreparedStatement latestStmt = conn.prepareStatement(getLatestNicknameQuery)) {
                    latestStmt.setString(1, uuid);
                    try (ResultSet latestRs = latestStmt.executeQuery()) {
                        if (latestRs.next()) {
                            String correctNickname = latestRs.getString("nickname");

                            updateStmt.setString(1, correctNickname);
                            updateStmt.setString(2, uuid);
                            updateStmt.setString(3, correctNickname);
                            updateStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private void fixRewardsToBeClaimed(Connection conn) throws SQLException {
        String findDuplicatesQuery =
                "SELECT user_uuid, nickname, MAX(created_at) as latest_creation " +
                        "FROM rewards_to_be_claimed " +
                        "GROUP BY user_uuid " +
                        "HAVING COUNT(DISTINCT nickname) > 1";

        String updateQuery =
                "UPDATE rewards_to_be_claimed " +
                        "SET nickname = ? " +
                        "WHERE user_uuid = ? AND nickname != ?";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findDuplicatesQuery);
             PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

            while (rs.next()) {
                String uuid = rs.getString("user_uuid");

                // Get the correct nickname from the latest record
                String getLatestNicknameQuery =
                        "SELECT nickname FROM rewards_to_be_claimed " +
                                "WHERE user_uuid = ? " +
                                "ORDER BY created_at DESC LIMIT 1";

                try (PreparedStatement latestStmt = conn.prepareStatement(getLatestNicknameQuery)) {
                    latestStmt.setString(1, uuid);
                    try (ResultSet latestRs = latestStmt.executeQuery()) {
                        if (latestRs.next()) {
                            String correctNickname = latestRs.getString("nickname");

                            updateStmt.setString(1, correctNickname);
                            updateStmt.setString(2, uuid);
                            updateStmt.setString(3, correctNickname);
                            updateStmt.executeUpdate();
                        }
                    }
                }
            }
        }
    }
}
