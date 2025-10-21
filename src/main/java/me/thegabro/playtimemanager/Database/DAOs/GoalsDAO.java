package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GoalsDAO {
    private final DatabaseHandler dbManager;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    public GoalsDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    public void removeGoalFromAllUsers(String goalToRemove) {
        String deleteQuery = "DELETE FROM completed_goals WHERE goal_name = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteQuery)) {

            ps.setString(1, goalToRemove);
            ps.executeUpdate();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error removing goal " + goalToRemove + " from all players: " + ex.getMessage());
        }
    }

    public void removeAllGoalsFromUser(String uuid) {
        String deleteQuery = "DELETE FROM completed_goals WHERE uuid = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteQuery)) {

            ps.setString(1, uuid);
            ps.executeUpdate();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error removing goals from user "+uuid+": " + ex.getMessage());
        }
    }

    public ArrayList<String> getCompletedGoals(String uuid) {
        String query = "SELECT goal_name FROM completed_goals WHERE user_uuid = ? ORDER BY received_at ASC";
        ArrayList<String> goals = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String goalName = rs.getString("goal_name");
                if (goalName != null && !goalName.trim().isEmpty()) {
                    goals.add(goalName.trim());
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting completed goals for UUID " + uuid + ": " + e.getMessage());
        }

        return goals;
    }

    public void addCompletedGoal(String uuid, String nickname, String goalName, boolean received) {
        String insertQuery = "INSERT INTO completed_goals " +
                "(goal_name, user_uuid, nickname, completed_on, received, received_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertQuery)) {

            ps.setString(1, goalName.trim());
            ps.setString(2, uuid);
            ps.setString(3, nickname);
            ps.setInt(4, received ? 1 : 0);

            // If received is true, set received_at to CURRENT_TIMESTAMP, otherwise NULL
            if (received) {
                ps.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
            } else {
                ps.setNull(5, java.sql.Types.TIMESTAMP);
            }

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error adding completed goal '" + goalName + "' for UUID " + uuid + ": " + e.getMessage());
        }
    }

    public void removeCompletedGoal(String uuid, String goalName) {
        String deleteQuery = "DELETE FROM completed_goals WHERE user_uuid = ? AND goal_name = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteQuery)) {

            ps.setString(1, uuid);
            ps.setString(2, goalName.trim());
            int deletedRows = ps.executeUpdate();

            if (deletedRows > 0) {
                plugin.getLogger().info("Removed goal '" + goalName + "' from player with UUID: " + uuid);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing completed goal '" + goalName + "' for UUID " + uuid + ": " + e.getMessage());
        }
    }

    public void updateGoalName(String oldName, String newName) {
        String updateQuery = "UPDATE completed_goals SET goal_name = ? WHERE goal_name = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateQuery)) {

            ps.setString(1, newName);
            ps.setString(2, oldName);
            ps.executeUpdate();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error updating goal name from " + oldName + " to " + newName + ": " + ex.getMessage());
        }
    }

    public void markGoalAsReceived(String uuid, String goalName) {
        String updateQuery = "UPDATE completed_goals SET received = 1, received_at = CURRENT_TIMESTAMP " +
                "WHERE user_uuid = ? AND goal_name = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateQuery)) {

            ps.setString(1, uuid);
            ps.setString(2, goalName.trim());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error marking goal '" + goalName + "' as received for UUID " + uuid + ": " + e.getMessage());
        }
    }

    public ArrayList<String> getNotReceivedGoals(String uuid) {
        String query = "SELECT goal_name FROM completed_goals " +
                "WHERE user_uuid = ? AND (received = 0 OR received_at IS NULL) " +
                "ORDER BY completed_on ASC";

        ArrayList<String> goals = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String goalName = rs.getString("goal_name");
                if (goalName != null && !goalName.trim().isEmpty()) {
                    goals.add(goalName.trim());
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error getting not received goals for UUID " + uuid + ": " + e.getMessage());
        }

        return goals;
    }
}
