package SQLiteDB;

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
            "uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "PRIMARY KEY (uuid)" +
            ");";
    public String GroupsTable = "CREATE TABLE IF NOT EXISTS groups (" +
            "group_name VARCHAR(32) NOT NULL," +
            "playtime_required BIGINT NOT NULL," +
            "PRIMARY KEY (group_name)" +
            ");";


    // SQL creation stuff, You can leave the blow stuff untouched.
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
            s.executeUpdate(GroupsTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
