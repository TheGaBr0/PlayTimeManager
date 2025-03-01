package me.thegabro.playtimemanager.SQLiteDB;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


import me.thegabro.playtimemanager.PlayTimeManager; // import your main class

public class SQLite extends PlayTimeDatabase {

    String dbname;
    PlayTimeManager plugin;
    public SQLite(PlayTimeManager instance){
        super(instance);
        this.plugin = instance;
        dbname = "play_time";
    }

    public String PlayTimeTable = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(32) NOT NULL UNIQUE," +
            "nickname VARCHAR(32) NOT NULL UNIQUE," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "completed_goals TEXT DEFAULT ''," +
            "last_seen DATETIME DEFAULT NULL,"+
            "first_join DATETIME DEFAULT NULL,"+
            "join_streak INT DEFAULT 1,"+
            "PRIMARY KEY (uuid)" +
            ");";

    public Connection getSQLConnection() {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not initialized");
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void load() {
        initialize(dbname);
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();

            // Create table if it doesn't exist
            s.executeUpdate(PlayTimeTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



}
