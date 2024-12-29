package SQLiteDB;

import java.sql.Connection;
import java.sql.ResultSet;
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
            "completed_goals TEXT DEFAULT ''," +
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

            //planned for removal, upgrade from 3.0.4 to 3.1 due to groups being transformed into goals

            // First check if the table exists
            boolean tableExists = false;
            try {
                ResultSet rs = s.executeQuery("SELECT 1 FROM play_time LIMIT 1");
                tableExists = true;
                rs.close();
            } catch (SQLException e) {
                // Table doesn't exist
            }

            if (tableExists) {
                // Only attempt column modification if the table exists
                try {
                    s.executeQuery("SELECT completed_goals FROM play_time LIMIT 1");
                } catch (SQLException e) {
                    // Add the column only if it doesn't exist
                    s.executeUpdate("ALTER TABLE play_time ADD COLUMN completed_goals TEXT DEFAULT ''");
                }
            }
            //----------------------------------------------------------

            // Create table if it doesn't exist
            s.executeUpdate(PlayTimeTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
