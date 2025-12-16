package me.thegabro.playtimemanager.Database.DatabaseTypes;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;


public class SQLiteDatabase implements Database {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HikariDataSource dataSource;
    private final String DBNAME = "play_time";

    // Table creation statements
    private static final String PLAY_TIME_TABLE = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(36) NOT NULL UNIQUE," +
            "nickname VARCHAR(36) NOT NULL UNIQUE," +
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
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "received_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";

    private static final String COMPLETED_GOALS_TABLE = "CREATE TABLE IF NOT EXISTS completed_goals (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "goal_name VARCHAR(36) NOT NULL," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "completed_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "received INTEGER NOT NULL DEFAULT 0," +
            "received_at DATETIME DEFAULT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";

    private static final String REWARDS_TO_BE_CLAIMED_TABLE = "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "expired BOOLEAN DEFAULT FALSE," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid)" +
            ");";


    @Override
    public void initialize() {
        File dataFolder = new File(plugin.getDataFolder(), DBNAME + ".db");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!dataFolder.exists()) {
            try {
                dataFolder.createNewFile();
                plugin.getLogger().info("Created new SQLite database file: " + DBNAME + ".db");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create database file: " + DBNAME + ".db", e);
                Bukkit.getPluginManager().disablePlugin(plugin);
                throw new RuntimeException("Failed to create SQLite database file", e);
            }
        }

        HikariConfig config = new HikariConfig();

        config.setLeakDetectionThreshold(60000);
        config.setJdbcUrl("jdbc:sqlite:" + dataFolder.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite only supports single connection writes
        config.setConnectionTimeout(30000);
        config.setConnectionTestQuery("SELECT 1");

        // SQLite specific optimizations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "30000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("temp_store", "MEMORY");

        if (plugin.getConfig().getBoolean("database-debug", false)) {
            plugin.getLogger().info("SQLite database path: " + dataFolder.getAbsolutePath());
        }

        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Successfully connected to SQLite database");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite connection", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("SQLite initialization failed", e);
        }
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
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
            plugin.getLogger().info("SQLite connection closed");
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
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("Failed to create SQLite tables", e);
        }
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }
}

