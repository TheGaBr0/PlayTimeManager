package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class JoinstreakDAO {

    private final DatabaseHandler dbManager;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public JoinstreakDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    public Set<String> getPlayersWithActiveStreaks() {
        Set<String> players = new HashSet<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("SELECT uuid FROM play_time WHERE absolute_join_streak > 0;");
            rs = ps.executeQuery();

            while (rs.next()) {
                players.add(rs.getString("uuid"));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
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

        return players;
    }

    public int getRelativeJoinStreak(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("SELECT relative_join_streak FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("relative_join_streak");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
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
        return 0; // Default return value if player not found or error occurs
    }

    public int getAbsoluteJoinStreak(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("SELECT absolute_join_streak FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("absolute_join_streak");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
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
        return 0; // Default return value if player not found or error occurs
    }

    public void setRelativeJoinStreak(String uuid, int value) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("UPDATE play_time SET relative_join_streak = ? WHERE uuid = ?;");
            ps.setInt(1, value);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void setAbsoluteJoinStreak(String uuid, int value) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("UPDATE play_time SET absolute_join_streak = ? WHERE uuid = ?;");
            ps.setInt(1, value);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }


    public void markRewardsAsExpired(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement(
                    "UPDATE rewards_to_be_claimed " +
                            "SET expired = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE user_uuid = ?"
            );

            ps.setBoolean(1, true);
            ps.setString(2, uuid);

            ps.executeUpdate(); // no need for batching here

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public ArrayList<RewardSubInstance> getRewardsToBeClaimed(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        ArrayList<RewardSubInstance> rewards = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement(
                    "SELECT main_instance_ID, required_joins, expired " +
                            "FROM rewards_to_be_claimed WHERE user_uuid = ? ORDER BY created_at;"
            );
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            while (rs.next()) {
                rewards.add(new RewardSubInstance(
                        rs.getInt("main_instance_ID"),
                        rs.getInt("required_joins"),
                        rs.getBoolean("expired")
                ));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }

        return rewards;
    }


    public ArrayList<RewardSubInstance> getReceivedRewards(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        ArrayList<RewardSubInstance> rewards = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement(
                    "SELECT main_instance_ID, required_joins " +
                            "FROM received_rewards WHERE user_uuid = ? ORDER BY received_at;"
            );
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            while (rs.next()) {
                rewards.add(new RewardSubInstance(
                        rs.getInt("main_instance_ID"),
                        rs.getInt("required_joins"),
                        false
                ));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }

        return rewards;
    }

    public void removeRewardFromAllUsers(Integer mainInstanceID) {
        Connection conn = null;
        PreparedStatement deleteReceivedPs = null;
        PreparedStatement deleteClaimablePs = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Use transaction for consistency


            deleteReceivedPs = conn.prepareStatement("DELETE FROM received_rewards WHERE main_instance_ID = ?;");

            deleteReceivedPs.setInt(1, mainInstanceID); // Exact match

            deleteReceivedPs.executeUpdate();

            deleteClaimablePs = conn.prepareStatement("DELETE FROM rewards_to_be_claimed WHERE main_instance_ID = ?;" );
            deleteClaimablePs.setInt(1, mainInstanceID); // Exact match

            deleteClaimablePs.executeUpdate();

            conn.commit();

        } catch (SQLException ex) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException rollbackEx) {
                plugin.getLogger().log(Level.SEVERE, "Error rolling back transaction", rollbackEx);
            }
            plugin.getLogger().log(Level.SEVERE, "Error removing reward " + mainInstanceID + " from all players: " + ex.getMessage());
        } finally {
            try {
                if (conn != null) conn.setAutoCommit(true);
                if (deleteReceivedPs != null) deleteReceivedPs.close();
                if (deleteClaimablePs != null) deleteClaimablePs.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void addReceivedReward(String uuid, String nickname, RewardSubInstance reward) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement(
                    "INSERT INTO received_rewards (user_uuid, nickname, main_instance_ID, required_joins, received_at) " +
                            "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP);"
            );
            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setInt(3, reward.mainInstanceID());
            ps.setInt(4, reward.requiredJoins());
            ps.executeUpdate();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error adding received reward", ex);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }


    public void addRewardToBeClaimed(String uuid, String nickname, RewardSubInstance reward) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement(
                    "INSERT INTO rewards_to_be_claimed (user_uuid, nickname, main_instance_ID, required_joins, created_at, updated_at, expired) " +
                            "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?);"
            );
            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setInt(3, reward.mainInstanceID());
            ps.setInt(4, reward.requiredJoins());
            ps.setBoolean(5, reward.expired());
            ps.executeUpdate();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error adding reward to be claimed", ex);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void removeRewardToBeClaimed(String uuid, RewardSubInstance reward) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM rewards_to_be_claimed WHERE user_uuid = ? AND main_instance_ID = ? AND required_joins = ?"
             )) {
            ps.setString(1, uuid);
            ps.setInt(2, reward.mainInstanceID());
            ps.setInt(3, reward.requiredJoins());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
    }

    public void removeReceivedReward(String uuid, RewardSubInstance reward) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM received_rewards WHERE user_uuid = ? AND main_instance_ID = ? AND required_joins = ?"
             )) {
            ps.setString(1, uuid);
            ps.setInt(2, reward.mainInstanceID());
            ps.setInt(3, reward.requiredJoins());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
    }

    public void resetAllUserRewards(String uuid) {
        Connection conn = null;
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // Delete all received rewards for this user
            ps1 = conn.prepareStatement("DELETE FROM received_rewards WHERE user_uuid = ?");
            ps1.setString(1, uuid);
            ps1.executeUpdate();

            // Delete all rewards to be claimed for this user
            ps2 = conn.prepareStatement("DELETE FROM rewards_to_be_claimed WHERE user_uuid = ?");
            ps2.setString(1, uuid);
            ps2.executeUpdate();

            conn.commit(); // Commit transaction
        } catch (SQLException ex) {
            try {
                if (conn != null) {
                    conn.rollback(); // Rollback on error
                }
            } catch (SQLException rollbackEx) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
            }
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit
                }
                if (ps1 != null) ps1.close();
                if (ps2 != null) ps2.close();
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

}
