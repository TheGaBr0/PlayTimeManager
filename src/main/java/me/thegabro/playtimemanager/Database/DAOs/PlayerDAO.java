package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public class PlayerDAO {
    private final DatabaseHandler dbManager;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public PlayerDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    public String getNickname(String uuid) {
        String query = "SELECT nickname FROM play_time WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
        return null;
    }

    public Long getPlaytime(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();
            ps = conn.prepareStatement("SELECT playtime FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("playtime");
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
        return 0L; // Default return value if the player is not found or an error occurs
    }

    public Long getArtificialPlaytime(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT artificial_playtime FROM play_time WHERE uuid = ?;");

            ps.setString(1, uuid);

            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("artificial_playtime");
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
        return null;
    }

    public Long getAFKPlaytime(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT afk_playtime FROM play_time WHERE uuid = ?;");

            ps.setString(1, uuid);

            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("afk_playtime");
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
        return null;
    }

    public String getUUIDFromNickname(String nickname) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT uuid FROM play_time WHERE nickname = ? LIMIT 1;");

            ps.setString(1, nickname);

            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("uuid");
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
        return null;
    }

    public List<String> getAllNicknames() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<String> nicknames = new ArrayList<>();
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT nickname FROM play_time;");

            rs = ps.executeQuery();

            while (rs.next()) {
                nicknames.add(rs.getString("nickname"));
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
        return nicknames;
    }

    public void updatePlaytime(String uuid, long newPlaytime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();


            ps = conn.prepareStatement("UPDATE play_time SET playtime = ? WHERE uuid = ?;");

            ps.setLong(1, newPlaytime);
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

    public void updateArtificialPlaytime(String uuid, long newArtificialPlaytime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("UPDATE play_time SET artificial_playtime = ? WHERE uuid = ?;");

            ps.setLong(1, newArtificialPlaytime);
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

    public void updateAFKPlaytime(String uuid, long newPlaytime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();


            ps = conn.prepareStatement("UPDATE play_time SET afk_playtime = ? WHERE uuid = ?;");

            ps.setLong(1, newPlaytime);
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

    public void updateNickname(String uuid, String newNickname) {
        Connection conn = null;
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;
        PreparedStatement ps3 = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            // Update play_time table
            ps1 = conn.prepareStatement("UPDATE play_time SET nickname = ? WHERE uuid = ?;");
            ps1.setString(1, newNickname);
            ps1.setString(2, uuid);
            ps1.executeUpdate();

            // Update completed_goals table
            ps3 = conn.prepareStatement("UPDATE completed_goals SET nickname = ? WHERE user_uuid = ?;");
            ps3.setString(1, newNickname);
            ps3.setString(2, uuid);
            ps3.executeUpdate();

            // Update received_rewards table
            ps2 = conn.prepareStatement("UPDATE received_rewards SET nickname = ? WHERE user_uuid = ?;");
            ps2.setString(1, newNickname);
            ps2.setString(2, uuid);
            ps2.executeUpdate();

            // Update rewards_to_be_claimed table
            ps3 = conn.prepareStatement("UPDATE rewards_to_be_claimed SET nickname = ? WHERE user_uuid = ?;");
            ps3.setString(1, newNickname);
            ps3.setString(2, uuid);
            ps3.executeUpdate();

            conn.commit();

        } catch (SQLException ex) {
            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback nickname update", rollbackEx);
                }
            }
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps1 != null) ps1.close();
                if (ps2 != null) ps2.close();
                if (ps3 != null) ps3.close();
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit
                    conn.close();
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }


    public void updateUUID(String newUUID, String nickname) {
        Connection conn = null;
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;
        PreparedStatement ps3 = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            // Update play_time table
            ps1 = conn.prepareStatement("UPDATE play_time SET uuid = ? WHERE nickname = ?;");
            ps1.setString(1, newUUID);
            ps1.setString(2, nickname);
            ps1.executeUpdate();

            // Update completed_goals table
            ps3 = conn.prepareStatement("UPDATE completed_goals SET user_uuid = ? WHERE nickname = ?;");
            ps3.setString(1, newUUID);
            ps3.setString(2, nickname);
            ps3.executeUpdate();

            // Update received_rewards table
            ps2 = conn.prepareStatement("UPDATE received_rewards SET user_uuid = ? WHERE nickname = ?;");
            ps2.setString(1, newUUID);
            ps2.setString(2, nickname);
            ps2.executeUpdate();

            // Update rewards_to_be_claimed table
            ps3 = conn.prepareStatement("UPDATE rewards_to_be_claimed SET user_uuid = ? WHERE nickname = ?;");
            ps3.setString(1, newUUID);
            ps3.setString(2, nickname);
            ps3.executeUpdate();

            conn.commit();

        } catch (SQLException ex) {
            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback UUID update", rollbackEx);
                }
            }
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps1 != null) ps1.close();
                if (ps2 != null) ps2.close();
                if (ps3 != null) ps3.close();
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset auto-commit
                    conn.close();
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public boolean playerExists(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("SELECT COUNT(*) FROM play_time WHERE uuid = ?;");

            ps.setString(1, uuid);

            rs = ps.executeQuery();

            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0;
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
        return false;
    }


    public void addNewPlayer(String uuid, String nickname, long playtime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = dbManager.getConnection();

            ps = conn.prepareStatement("INSERT INTO play_time " +
                    "(uuid, nickname, playtime, artificial_playtime, afk_playtime, first_join) " +
                    "VALUES (?, ?, ?, ?, ?, ?);");

            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setLong(3, playtime);
            ps.setLong(4, 0);
            ps.setLong(5, 0);
            ps.setLong(6, Instant.now().toEpochMilli());

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

    public void updateLastSeen(String uuid, Instant lastSeen) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE play_time SET last_seen = ? WHERE uuid = ?")) {

            if (lastSeen != null) {
                ps.setLong(1, lastSeen.toEpochMilli());
            } else {
                ps.setNull(1, Types.BIGINT);
            }

            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating last_seen time: " + e.getMessage());
        }
    }

    public Instant getLastSeen(String uuid) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT last_seen FROM play_time WHERE uuid = ?")) {

            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long millis = rs.getLong("last_seen");
                if (!rs.wasNull()) {
                    return Instant.ofEpochMilli(millis);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving last_seen: " + e.getMessage());
        }
        return null;
    }

    public void updateFirstJoin(String uuid, Instant firstJoin) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE play_time SET first_join = ? WHERE uuid = ?")) {

            if (firstJoin != null) {
                ps.setLong(1, firstJoin.toEpochMilli());
            } else {
                ps.setNull(1, Types.BIGINT);
            }

            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating first_join time: " + e.getMessage());
        }
    }


    public Instant getFirstJoin(String uuid) {
        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT first_join FROM play_time WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long millis = rs.getLong("first_join");
                if (!rs.wasNull()) {
                    return Instant.ofEpochMilli(millis);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving first_join: " + e.getMessage());
        }
        return null;
    }

    public void resetUserInDatabase(String uuid) {
        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Reset main play_time table
            String updatePlayTimeQuery = "UPDATE play_time SET " +
                    "playtime = 0, " +
                    "artificial_playtime = 0, " +
                    "afk_playtime = 0, " +
                    "last_seen = NULL, " +
                    "first_join = NULL, " +
                    "relative_join_streak = 0, " +
                    "absolute_join_streak = 0 " +
                    "WHERE uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(updatePlayTimeQuery)) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }

            // 2. Delete all completed goals
            String deleteGoalsQuery = "DELETE FROM completed_goals WHERE user_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteGoalsQuery)) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }

            // 3. Delete all received rewards
            String deleteReceivedRewardsQuery = "DELETE FROM received_rewards WHERE user_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteReceivedRewardsQuery)) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }

            // 4. Delete all rewards to be claimed
            String deleteClaimableRewardsQuery = "DELETE FROM rewards_to_be_claimed WHERE user_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteClaimableRewardsQuery)) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }

            conn.commit(); // Commit all changes atomically

            plugin.getLogger().info("Successfully reset all data for user: " + uuid);

        } catch (SQLException e) {
            // Rollback on any error
            if (conn != null) {
                try {
                    conn.rollback();
                    plugin.getLogger().severe("Database reset failed for user " + uuid + ", rolled back changes: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Failed to rollback reset transaction for user " + uuid + ": " + rollbackEx.getMessage());
                }
            }
            e.printStackTrace();
        } finally {
            // Clean up connection
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Reset auto-commit
                    conn.close();
                } catch (SQLException ex) {
                    plugin.getLogger().severe("Error closing connection after user reset: " + ex.getMessage());
                }
            }
        }
    }

    public ArrayList<String> getPlayersSeenSince(Instant since) {
        ArrayList<String> uuids = new ArrayList<>();
        String query = "SELECT uuid FROM play_time WHERE last_seen >= ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setLong(1, since.toEpochMilli());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    uuids.add(rs.getString("uuid"));
                }
            }

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }

        return uuids;
    }

}