package SQLiteDB;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

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
            "artificial_playtime BIGINT," +
            "PRIMARY KEY (uuid)" +
            ");";


    // SQL creation stuff, You can leave the blow stuff untouched.
    public Connection getSQLConnection() {
        File dataFolder = new File(plugin.getDataFolder(), dbname+".db");
        if (!dataFolder.exists()){
            try {
                dataFolder.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "File write error: "+dbname+".db");
            }
        }
        try {
            if(connection!=null&&!connection.isClosed()){
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            return connection;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,"SQLite exception on initialize", ex);
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().log(Level.SEVERE, "You need the SQLite JBDC library. Google it. Put it in /lib folder.");
        }
        return null;
    }

    public void load() {
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();
            plugin.getLogger().info(String.valueOf(s.executeUpdate(SQLiteCreateTable)));
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        initialize();
    }
}
