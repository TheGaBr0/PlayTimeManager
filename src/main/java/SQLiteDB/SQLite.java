package SQLiteDB;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import me.thegabro.playtimemanager.PlayTimeManager; // import your main class

public class SQLite extends Database{

    String dbname;
    PlayTimeManager plugin;
    public SQLite(PlayTimeManager instance){
        super(instance);
        this.plugin = instance;
        dbname = "play_time";
    }

    public String SQLiteCreateTable = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "PRIMARY KEY (uuid)" +
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
            s.executeUpdate(SQLiteCreateTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
