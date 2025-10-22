package me.thegabro.playtimemanager.Database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.thegabro.playtimemanager.PlayTimeManager;


public class SQLiteDatabase implements Database {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HikariDataSource dataSource;
    private final String dbName;

    // Table creation statements
    private static final String PLAY_TIME_TABLE = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(32) NOT NULL UNIQUE," +
            "nickname VARCHAR(32) NOT NULL UNIQUE," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "afk_playtime BIGINT NOT NULL," +
            "last_seen BIGINT DEFAULT NULL," +
            "first_join BIGINT DEFAULT NULL," +
            "relative_join_streak INT DEFAULT 0," +
            "absolute_join_streak INT DEFAULT 0," +
            "PRIMARY KEY (uuid)" +
            ");";

    private static final String RECEIVED_REWARDS_TABLE = "CREATE TABLE IF NOT EXISTS received_rewards (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "received_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";

    private static final String COMPLETED_GOALS_TABLE = "CREATE TABLE IF NOT EXISTS completed_goals (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "goal_name VARCHAR(32) NOT NULL," +
            "user_uuid VARCHAR(32) NOT NULL," +
            "nickname VARCHAR(32) NOT NULL," +
            "completed_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "received INTEGER NOT NULL DEFAULT 0," +
            "received_at DATETIME DEFAULT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";

    private static final String REWARDS_TO_BE_CLAIMED_TABLE = "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
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

    public SQLiteDatabase() {
        this.dbName = "play_time";
        initialize();
    }

    @Override
    public void initialize() {
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

        // Create tables after initializing connection
        createTables();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public void createTables() {
        try (Connection conn = getConnection();
             Statement s = conn.createStatement()) {

            s.executeUpdate(PLAY_TIME_TABLE);
            s.executeUpdate(COMPLETED_GOALS_TABLE);
            s.executeUpdate(RECEIVED_REWARDS_TABLE);
            s.executeUpdate(REWARDS_TO_BE_CLAIMED_TABLE);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite tables", e);
        }
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }
}

