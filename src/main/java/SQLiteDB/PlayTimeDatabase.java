package SQLiteDB;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import Users.DBUser;

import me.thegabro.playtimemanager.PlayTimeManager;


public abstract class PlayTimeDatabase {
    PlayTimeManager plugin;
    Connection connection;
    protected static HikariDataSource dataSource;

    public PlayTimeDatabase(PlayTimeManager instance){
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
                    return new Object[] {(greaterPlayers * 100.0) / totalPlayers , greaterPlayers, totalPlayers};
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

    public ArrayList<String> getTopPlayersByPlaytime(int topN) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        ArrayList<String> topPlayers = new ArrayList<>();
        try {
            conn = getSQLConnection();

            ps = conn.prepareStatement("SELECT uuid FROM play_time " +
                    "ORDER BY (playtime + artificial_playtime) DESC LIMIT ?;");

            ps.setInt(1, topN);

            rs = ps.executeQuery();

            while (rs.next()) {

                String uuid = rs.getString("uuid");
                topPlayers.add(uuid);
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

    public DBUser getTopPlayerAtPosition(int position){
        ArrayList<String> topPlayers = getTopPlayersByPlaytime(position);
        ArrayList<DBUser> topDBUsers = new ArrayList<>();
        DBUser user;
        for(String uuid : topPlayers){
            user = plugin.getUsersManager().getOnlineUserByUUID(uuid);
            if(user == null)
                user = DBUser.fromUUID(uuid);

            topDBUsers.add(user);
        }

        try{
            return topDBUsers.get(position-1);
        }catch(IndexOutOfBoundsException exp){
            return null;
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


    //planned for removal, upgrade from 3.0.4 to 3.1 due to groups being transformed into goals
    public Map<String, Long> getAllGroupsData() {
        Map<String, Long> groups = new HashMap<>();
        try (Connection conn = getSQLConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT group_name, playtime_required FROM groups;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String groupName = rs.getString("group_name");
                Long time = rs.getLong("playtime_required");
                groups.put(groupName, time);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), e);
        }
        return groups;
    }
    public void dropGroupsTable() {
        String dropTableSQL = "DROP TABLE IF EXISTS groups;";

        try (Connection connection = getSQLConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(dropTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
