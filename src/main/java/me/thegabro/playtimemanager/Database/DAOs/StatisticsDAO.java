package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class StatisticsDAO {

    private final DatabaseHandler dbManager;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public StatisticsDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    public Double getAveragePlaytime() {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = dbManager.getConnection();

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
            conn = dbManager.getConnection();

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
            conn = dbManager.getConnection();

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
}
