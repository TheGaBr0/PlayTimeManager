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

    public String PlayTimeTable = "CREATE TABLE play_time_new (" +
            "uuid VARCHAR(32) NOT NULL UNIQUE," +
            "nickname VARCHAR(32) NOT NULL UNIQUE," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "afk_playtime BIGINT NOT NULL," +
            "completed_goals TEXT DEFAULT ''," +
            "last_seen DATETIME DEFAULT NULL," +
            "first_join DATETIME DEFAULT NULL," +
            "relative_join_streak INT DEFAULT 0," +
            "absolute_join_streak INT DEFAULT 0," +
            "PRIMARY KEY (uuid)" +
            ");";

    public String ReceivedRewardsTable = "CREATE TABLE IF NOT EXISTS received_rewards (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "received_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";

    public String RewardsToBeClaimedTable = "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "expired BOOLEAN DEFAULT FALSE," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
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

            s.executeUpdate(PlayTimeTable);
            s.executeUpdate(ReceivedRewardsTable);
            s.executeUpdate(RewardsToBeClaimedTable);

            s.close();
        } catch (SQLException ignored) {}
    }



}
