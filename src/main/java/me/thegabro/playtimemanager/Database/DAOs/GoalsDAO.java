package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
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

    public GoalsDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    public void removeGoalFromAllUsers(String goalToRemove) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            // First, get all players who have completed goals
            ps = conn.prepareStatement("SELECT uuid, completed_goals FROM play_time WHERE completed_goals IS NOT NULL AND completed_goals != '';");
            rs = ps.executeQuery();

            PreparedStatement updateStmt = conn.prepareStatement("UPDATE play_time SET completed_goals = ? WHERE uuid = ?;");

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String completedGoals = rs.getString("completed_goals");

                if (completedGoals == null || completedGoals.isEmpty()) {
                    continue;
                }

                // Convert to ArrayList, remove the goal, and convert back to string
                ArrayList<String> goals = new ArrayList<>();
                for (String goal : completedGoals.split(",")) {
                    String trimmedGoal = goal.trim();
                    if (!trimmedGoal.isEmpty() && !trimmedGoal.equals(goalToRemove)) {
                        goals.add(trimmedGoal);
                    }
                }

                // Convert back to comma-separated string
                String updatedGoals = goals.stream()
                        .map(String::trim)
                        .filter(goal -> !goal.isEmpty())
                        .collect(Collectors.joining(","));

                // Update the database
                updateStmt.setString(1, updatedGoals);
                updateStmt.setString(2, uuid);
                updateStmt.executeUpdate();
            }

            updateStmt.close();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error removing goal " + goalToRemove + " from all players: " + ex.getMessage());
        } finally {
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public ArrayList<String> getCompletedGoals(String uuid) {
        String query = "SELECT completed_goals FROM play_time WHERE uuid = ?";
        ArrayList<String> goals = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, uuid);
            var rs = stmt.executeQuery();

            if (rs.next()) {
                String completedGoals = rs.getString("completed_goals");
                if (completedGoals != null && !completedGoals.isEmpty()) {
                    // Split the goals string into individual goals and add them to the ArrayList
                    String[] goalsArray = completedGoals.split(",");
                    for (String goal : goalsArray) {
                        String trimmedGoal = goal.trim();
                        if (!trimmedGoal.isEmpty()) {
                            goals.add(trimmedGoal);
                        }
                    }
                }
            }
            return goals;

        } catch (SQLException e) {
            e.printStackTrace();
            return goals;
        }
    }

    public void updateCompletedGoals(String uuid, ArrayList<String> goals) {
        String updateQuery = "UPDATE play_time SET completed_goals = ? WHERE uuid = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

            // Convert ArrayList to comma-separated string, filtering out empty goals
            String updatedGoals = goals.stream()
                    .map(String::trim)
                    .filter(goal -> !goal.isEmpty())
                    .collect(Collectors.joining(","));

            updateStmt.setString(1, updatedGoals);
            updateStmt.setString(2, uuid);
            updateStmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateGoalName(String oldName, String newName) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT uuid, completed_goals FROM play_time WHERE completed_goals IS NOT NULL AND completed_goals != '';");
            rs = ps.executeQuery();

            PreparedStatement updateStmt = conn.prepareStatement("UPDATE play_time SET completed_goals = ? WHERE uuid = ?;");

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String completedGoals = rs.getString("completed_goals");

                if (completedGoals == null || completedGoals.isEmpty()) {
                    continue;
                }

                String[] goals = completedGoals.split(",");
                boolean needsUpdate = false;

                for (int i = 0; i < goals.length; i++) {
                    if (goals[i].trim().equals(oldName)) {
                        goals[i] = newName;
                        needsUpdate = true;
                    }
                }

                if (needsUpdate) {
                    String updatedGoals = String.join(",", goals);
                    updateStmt.setString(1, updatedGoals);
                    updateStmt.setString(2, uuid);
                    updateStmt.executeUpdate();
                }
            }

            updateStmt.close();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error updating goal name from " + oldName + " to " + newName + ": " + ex.getMessage());
        } finally {
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }
}
