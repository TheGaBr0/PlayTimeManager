package me.thegabro.playtimemanager.SQLiteDB;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

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

    public void updateNickname(String uuid, String newNickname) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("UPDATE play_time SET nickname = ? WHERE uuid = ?;");

            ps.setString(1, newNickname);
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

    public void updateUUID(String uuid, String nickname) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("UPDATE play_time SET uuid = ? WHERE nickname = ?;");

            ps.setString(1, uuid);         // Set the new UUID
            ps.setString(2, nickname);     // Use nickname in WHERE clause

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

            ps = conn.prepareStatement("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime) VALUES (?, ?, ?, ?);");

            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setLong(3, playtime);
            ps.setLong(4, 0);

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

    public int getJoinStreak(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT join_streak FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("join_streak");
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

    public void incrementJoinStreak(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("UPDATE play_time SET join_streak = join_streak + 1 WHERE uuid = ?;");
            ps.setString(1, uuid);
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

    public void resetJoinStreak(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("UPDATE play_time SET join_streak = 0 WHERE uuid = ?;");
            ps.setString(1, uuid);
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

    public Set<String> getPlayersWithinTimeInterval(long intervalSeconds) {
        Set<String> players = new HashSet<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getSQLConnection();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoffTime = now.minusSeconds(intervalSeconds);

            // Use timestamp comparison directly
            ps = conn.prepareStatement(
                    "SELECT uuid FROM play_time WHERE last_seen IS NOT NULL AND " +
                            "last_seen >= ?");

            ps.setTimestamp(1, Timestamp.valueOf(cutoffTime));

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

    public Set<String> getPlayersWithActiveStreaks() {
        Set<String> players = new HashSet<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT uuid FROM play_time WHERE join_streak > 0;");
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

    public LinkedHashSet<Float> getRewardsToBeClaimed(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        LinkedHashSet<Float> rewards = new LinkedHashSet<>();

        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT rewards_to_be_claimed FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                String rewardsStr = rs.getString("rewards_to_be_claimed");
                if (rewardsStr != null && !rewardsStr.isEmpty()) {
                    String[] rewardArray = rewardsStr.split(",");
                    for (String reward : rewardArray) {
                        try {
                            rewards.add(Float.parseFloat(reward.trim()));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid reward format: " + reward);
                        }
                    }
                }
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

    public LinkedHashSet<Float> getReceivedRewards(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        LinkedHashSet<Float> rewards = new LinkedHashSet<>();

        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT received_rewards FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                String rewardsStr = rs.getString("received_rewards");
                if (rewardsStr != null && !rewardsStr.isEmpty()) {
                    String[] rewardArray = rewardsStr.split(",");
                    for (String reward : rewardArray) {
                        try {
                            rewards.add(Float.parseFloat(reward.trim()));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid reward format: " + reward);
                        }
                    }
                }
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

    public void updateReceivedRewards(String uuid, LinkedHashSet<Float> rewards) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("UPDATE play_time SET received_rewards = ? WHERE uuid = ?;");

            String rewardsStr = rewards.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            ps.setString(1, rewardsStr);
            ps.setString(2, uuid);

            ps.executeUpdate();
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

    public void updateRewardsToBeClaimed(String uuid, LinkedHashSet<Float> rewards) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("UPDATE play_time SET rewards_to_be_claimed = ? WHERE uuid = ?;");

            String rewardsStr = rewards.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            ps.setString(1, rewardsStr);
            ps.setString(2, uuid);

            ps.executeUpdate();
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

    public void removeRewardFromAllUsers(int rewardID) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();

            // First, get all players who have received rewards or have rewards to be claimed
            ps = conn.prepareStatement("SELECT uuid, received_rewards, rewards_to_be_claimed FROM play_time WHERE " +
                    "(received_rewards IS NOT NULL AND received_rewards != '') OR " +
                    "(rewards_to_be_claimed IS NOT NULL AND rewards_to_be_claimed != '');");
            rs = ps.executeQuery();

            PreparedStatement updateStmt = conn.prepareStatement("UPDATE play_time SET received_rewards = ?, rewards_to_be_claimed = ? WHERE uuid = ?;");

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String receivedRewards = rs.getString("received_rewards");
                String rewardsToBeClaimed = rs.getString("rewards_to_be_claimed");

                // Process received_rewards column
                LinkedHashSet<Float> receivedRewardsList = new LinkedHashSet<>();
                if (receivedRewards != null && !receivedRewards.isEmpty()) {
                    for (String reward : receivedRewards.split(",")) {
                        try {
                            float rewardValue = Float.parseFloat(reward.trim());
                            // Keep only if the integer part doesn't match the rewardID
                            if (Math.floor(rewardValue) != rewardID) {
                                receivedRewardsList.add(rewardValue);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid entries
                        }
                    }
                }

                // Process rewards_to_be_claimed column
                LinkedHashSet<Float> rewardsToBeClaimedList = new LinkedHashSet<>();
                if (rewardsToBeClaimed != null && !rewardsToBeClaimed.isEmpty()) {
                    for (String reward : rewardsToBeClaimed.split(",")) {
                        try {
                            float rewardValue = Float.parseFloat(reward.trim());
                            // Keep only if the integer part doesn't match the rewardID
                            if (Math.floor(rewardValue) != rewardID) {
                                rewardsToBeClaimedList.add(rewardValue);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid entries
                        }
                    }
                }

                // Convert back to comma-separated strings
                String updatedReceivedRewards = receivedRewardsList.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

                String updatedRewardsToBeClaimed = rewardsToBeClaimedList.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

                // Update the database
                updateStmt.setString(1, updatedReceivedRewards);
                updateStmt.setString(2, updatedRewardsToBeClaimed);
                updateStmt.setString(3, uuid);
                updateStmt.executeUpdate();
            }

            updateStmt.close();

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error removing reward " + rewardID + " from all players: " + ex.getMessage());
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
