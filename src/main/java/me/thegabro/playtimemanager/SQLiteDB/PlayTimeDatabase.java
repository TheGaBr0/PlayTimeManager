package me.thegabro.playtimemanager.SQLiteDB;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import me.thegabro.playtimemanager.PlayTimeManager;


public abstract class PlayTimeDatabase {
    PlayTimeManager plugin;
    Connection connection;
    protected static HikariDataSource dataSource;

    public PlayTimeDatabase(PlayTimeManager instance) {
        plugin = instance;
    }

    public abstract Connection getSQLConnection();

    public abstract void load();

    public void initialize(String dbName) {
        File dataFolder = new File(plugin.getDataFolder(), dbName + ".db");
        if (!dataFolder.exists()) {
            try {
                dataFolder.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "File write error: " + dbName + ".db");
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dataFolder.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(20);
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "30000");

        dataSource = new HikariDataSource(config);
    }

    public String getNickname(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("SELECT nickname FROM play_time WHERE uuid = ?;");

            ps.setString(1, uuid);

            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("nickname");
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

    public Long getPlaytime(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();
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
            conn = getSQLConnection();

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
            conn = getSQLConnection();

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
            conn = getSQLConnection();

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
            conn = getSQLConnection();

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
            conn = getSQLConnection();


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
            conn = getSQLConnection();

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
            conn = getSQLConnection();


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
            conn = getSQLConnection();
            conn.setAutoCommit(false);

            // Update play_time table
            ps1 = conn.prepareStatement("UPDATE play_time SET nickname = ? WHERE uuid = ?;");
            ps1.setString(1, newNickname);
            ps1.setString(2, uuid);
            ps1.executeUpdate();

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
            conn = getSQLConnection();
            conn.setAutoCommit(false);

            // Update play_time table
            ps1 = conn.prepareStatement("UPDATE play_time SET uuid = ? WHERE nickname = ?;");
            ps1.setString(1, newUUID);
            ps1.setString(2, nickname);
            ps1.executeUpdate();

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
            conn = getSQLConnection();

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
            conn = getSQLConnection();

            ps = conn.prepareStatement("INSERT INTO play_time " +
                    "(uuid, nickname, playtime, artificial_playtime, afk_playtime, first_join) " +
                    "VALUES (?, ?, ?, ?, ?, ?);");

            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setLong(3, playtime);
            ps.setLong(4, 0);
            ps.setLong(5, 0);
            LocalDateTime truncated = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ps.setTimestamp(6, Timestamp.valueOf(truncated));
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

    public Double getAveragePlaytime() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("SELECT AVG(playtime + artificial_playtime) AS avg_playtime FROM play_time;");

            rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getDouble("avg_playtime");
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

    public Object[] getPercentageOfPlayers(long playtime) {
        Connection conn = null;
        PreparedStatement psTotal = null;
        PreparedStatement psGreater = null;
        ResultSet rsTotal = null;
        ResultSet rsGreater = null;
        try {
            conn = getSQLConnection();

            psTotal = conn.prepareStatement("SELECT COUNT(*) AS total_players FROM play_time;");

            rsTotal = psTotal.executeQuery();

            psGreater = conn.prepareStatement("SELECT COUNT(*) AS greater_players FROM play_time WHERE (playtime + artificial_playtime) >= ?;");

            psGreater.setLong(1, playtime);

            rsGreater = psGreater.executeQuery();

            if (rsTotal.next() && rsGreater.next()) {
                int totalPlayers = rsTotal.getInt("total_players");
                int greaterPlayers = rsGreater.getInt("greater_players");

                if (totalPlayers > 0) {
                    return new Object[]{(greaterPlayers * 100.0) / totalPlayers, greaterPlayers, totalPlayers};
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (rsTotal != null)
                    rsTotal.close();
                if (rsGreater != null)
                    rsGreater.close();
                if (psTotal != null)
                    psTotal.close();
                if (psGreater != null)
                    psGreater.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null;
    }

    public Map<String, String> getTopPlayersByPlaytime(int topN) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        Map<String, String> topPlayers = new LinkedHashMap<>();
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("SELECT uuid,nickname FROM play_time " +
                    "ORDER BY (playtime + artificial_playtime) DESC LIMIT ?;");

            ps.setInt(1, topN);

            rs = ps.executeQuery();

            while (rs.next()) {

                String uuid = rs.getString("uuid");
                String nickname = rs.getString("nickname");

                topPlayers.put(uuid, nickname != null ? nickname : uuid);

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
        return topPlayers;
    }

    public void removeGoalFromAllUsers(String goalToRemove) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();

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

        try (Connection conn = getSQLConnection();
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

        try (Connection conn = getSQLConnection();
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

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void updateGoalName(String oldName, String newName) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();

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

    public void updateLastSeen(String uuid, LocalDateTime lastSeen) {
        try (Connection connection = getSQLConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE play_time SET last_seen = ? WHERE uuid = ?")) {

            if (lastSeen != null) {
                // Convert LocalDateTime to Timestamp for SQL DATETIME storage
                LocalDateTime truncated = lastSeen.truncatedTo(ChronoUnit.SECONDS);
                ps.setTimestamp(1, Timestamp.valueOf(truncated));
            } else {
                ps.setNull(1, Types.TIMESTAMP); // Handle null case properly
            }

            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating last_seen time: " + e.getMessage());
        }
    }


    public LocalDateTime getLastSeen(String uuid) {
        try (Connection connection = getSQLConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT last_seen FROM play_time WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("last_seen");
                return timestamp != null ? timestamp.toLocalDateTime() : null;
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public void updateFirstJoin(String uuid, LocalDateTime firstJoin) {
        try (Connection connection = getSQLConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE play_time SET first_join = ? WHERE uuid = ?")) {

            if (firstJoin != null) {
                // Convert LocalDateTime to Timestamp for SQL DATETIME storage
                LocalDateTime truncated = firstJoin.truncatedTo(ChronoUnit.SECONDS);
                ps.setTimestamp(1, Timestamp.valueOf(truncated));
            } else {
                ps.setNull(1, Types.TIMESTAMP); // Handle null case properly
            }

            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating first_join time: " + e.getMessage());
        }
    }


    public LocalDateTime getFirstJoin(String uuid) {
        try (Connection connection = getSQLConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT first_join FROM play_time WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("first_join");
                return timestamp != null ? timestamp.toLocalDateTime() : null;
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public Set<String> getPlayersWithActiveStreaks() {
        Set<String> players = new HashSet<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
            conn = getSQLConnection();
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
        try (Connection conn = getSQLConnection();
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
        try (Connection conn = getSQLConnection();
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
            conn = getSQLConnection();
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