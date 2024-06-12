package SQLiteDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import UsersDatabases.DBUser;
import org.bukkit.entity.Player;

import me.thegabro.playtimemanager.PlayTimeManager;


public abstract class Database {
    PlayTimeManager plugin;
    Connection connection;
    // The name of the table we created back in SQLite class.
    public String table = "play_time";
    public int tokens = 0;
    public Database(PlayTimeManager instance){
        plugin = instance;
    }

    public abstract Connection getSQLConnection();

    public abstract void load();

    public void initialize(){
        connection = getSQLConnection();
        try{
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?");
            ResultSet rs = ps.executeQuery();
            close(ps,rs);

        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Unable to retreive connection", ex);
        }
    }

    public String getNickname(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to select nickname for the player with the specified uuid
            ps = conn.prepareStatement("SELECT nickname FROM play_time WHERE uuid = ?;");

            // Set the uuid parameter in the prepared statement
            ps.setString(1, uuid);

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Check if a result is returned
            if (rs.next()) {
                // Retrieve and return the nickname value
                return rs.getString("nickname");
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null; // Return null if the player is not found or an error occurs
    }

    public Long getTotalPlaytime(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT playtime, artificial_playtime FROM play_time WHERE uuid = ?;");
            ps.setString(1, uuid);

            rs = ps.executeQuery();
            if (rs.next()) {
                long playtime = rs.getLong("playtime");
                long artificialPlaytime = rs.getLong("artificial_playtime");
                return playtime + artificialPlaytime;
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
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to select artificial_playtime for the player with the specified uuid
            ps = conn.prepareStatement("SELECT artificial_playtime FROM play_time WHERE uuid = ?;");

            // Set the uuid parameter in the prepared statement
            ps.setString(1, uuid);

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Check if a result is returned
            if (rs.next()) {
                // Retrieve the artificial_playtime value
                return rs.getLong("artificial_playtime");
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null; // Return null if the player is not found or an error occurs
    }

    public String getUUIDFromNickname(String nickname) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to select uuid for the player with the specified nickname
            ps = conn.prepareStatement("SELECT uuid FROM play_time WHERE nickname = ? LIMIT 1;");

            // Set the nickname parameter in the prepared statement
            ps.setString(1, nickname);

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Check if a result is returned
            if (rs.next()) {
                // Retrieve and return the UUID value
                return rs.getString("uuid");
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null; // Return null if the nickname is not found or an error occurs
    }

    public List<String> getAllNicknames() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<String> nicknames = new ArrayList<>();
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to select all nicknames from the play_time table
            ps = conn.prepareStatement("SELECT nickname FROM play_time;");

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Iterate through the result set to retrieve nicknames
            while (rs.next()) {
                // Retrieve and add the nickname value to the list
                nicknames.add(rs.getString("nickname"));
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return nicknames; // Return the list of nicknames
    }

    public void updatePlaytime(String uuid, long newPlaytime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to update the playtime for the player with the specified uuid
            ps = conn.prepareStatement("UPDATE play_time SET playtime = ? WHERE uuid = ?;");

            // Set the parameters in the prepared statement
            ps.setLong(1, newPlaytime);
            ps.setString(2, uuid);

            // Execute the update
            ps.executeUpdate();
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void updateArtificialPlaytime(String uuid, long newArtificialPlaytime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to update the artificial_playtime for the player with the specified uuid
            ps = conn.prepareStatement("UPDATE play_time SET artificial_playtime = ? WHERE uuid = ?;");

            // Set the parameters in the prepared statement
            ps.setLong(1, newArtificialPlaytime);
            ps.setString(2, uuid);

            // Execute the update
            ps.executeUpdate();
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public void updateNickname(String uuid, String newNickname) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to update the nickname for the player with the specified uuid
            ps = conn.prepareStatement("UPDATE play_time SET nickname = ? WHERE uuid = ?;");

            // Set the parameters in the prepared statement
            ps.setString(1, newNickname);
            ps.setString(2, uuid);

            // Execute the update
            ps.executeUpdate();
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public boolean playerExists(String uuid) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to check if the player exists in the play_time table
            ps = conn.prepareStatement("SELECT COUNT(*) FROM play_time WHERE uuid = ?;");

            // Set the uuid parameter in the prepared statement
            ps.setString(1, uuid);

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Retrieve the count of players with the specified uuid
            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0; // Return true if count is greater than 0, indicating the player exists
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return false; // Return false if an error occurs or the player is not found
    }


    public void addNewPlayer(String uuid, String nickname, long playtime) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to insert a new player into the play_time table
            ps = conn.prepareStatement("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime) VALUES (?, ?, ?, ?);");

            // Set the parameters in the prepared statement
            ps.setString(1, uuid);
            ps.setString(2, nickname);
            ps.setLong(3, playtime);
            ps.setLong(4, 0);

            // Execute the insert
            ps.executeUpdate();
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
    }

    public Double getAveragePlaytime() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to calculate the average playtime
            ps = conn.prepareStatement("SELECT AVG(playtime) AS avg_playtime FROM play_time;");

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Check if a result is returned
            if (rs.next()) {
                // Retrieve and return the average playtime value
                return rs.getDouble("avg_playtime");
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null; // Return null if an error occurs
    }

    public Double getPercentageOfPlayersWithPlaytimeGreaterThan(long playtime) {
        Connection conn = null;
        PreparedStatement psTotal = null;
        PreparedStatement psGreater = null;
        ResultSet rsTotal = null;
        ResultSet rsGreater = null;
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to count the total number of players
            psTotal = conn.prepareStatement("SELECT COUNT(*) AS total_players FROM play_time;");

            // Execute the query and get the result set for the total number of players
            rsTotal = psTotal.executeQuery();

            // Prepare the SQL statement to count the number of players with playtime greater than the given value
            psGreater = conn.prepareStatement("SELECT COUNT(*) AS greater_players FROM play_time WHERE playtime > ?;");

            // Set the playtime parameter in the prepared statement
            psGreater.setLong(1, playtime);

            // Execute the query and get the result set for players with playtime greater than the given value
            rsGreater = psGreater.executeQuery();

            // Check if results are returned
            if (rsTotal.next() && rsGreater.next()) {
                int totalPlayers = rsTotal.getInt("total_players");
                int greaterPlayers = rsGreater.getInt("greater_players");

                // Calculate and return the percentage of players with playtime greater than the given value
                if (totalPlayers > 0) {
                    return (greaterPlayers * 100.0) / totalPlayers;
                }
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
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
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return null; // Return null if an error occurs
    }

    public List<DBUser> getTopPlayersByPlaytime(int topN) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<DBUser> topPlayers = new ArrayList<>();
        try {
            // Establish connection to the database
            conn = getSQLConnection();

            // Prepare the SQL statement to select the top N players with the highest playtime
            ps = conn.prepareStatement("SELECT uuid FROM play_time ORDER BY playtime DESC LIMIT ?;");

            // Set the topN parameter in the prepared statement
            ps.setInt(1, topN);

            // Execute the query and get the result set
            rs = ps.executeQuery();

            // Iterate through the result set to retrieve player data
            while (rs.next()) {
                // Retrieve and add the player data to the list
                String uuid = rs.getString("uuid");
                DBUser userByUUID = DBUser.fromUUID(uuid);
                topPlayers.add(userByUUID);
            }
        } catch (SQLException ex) {
            // Log any SQL exceptions that occur during query execution
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            // Ensure that resources are closed to avoid memory leaks
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException ex) {
                // Log any SQL exceptions that occur during resource closure
                plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
            }
        }
        return topPlayers; // Return the list of top players
    }

    public DBUser getTopPlayerAtPosition(int position){
        List<DBUser> topPlayers = getTopPlayersByPlaytime(position);
        return topPlayers.get(position);
    }

    public void close(PreparedStatement ps,ResultSet rs){
        try {
            if (ps != null)
                ps.close();
            if (rs != null)
                rs.close();
        } catch (SQLException ex) {
            Error.close(plugin, ex);
        }
    }
}
