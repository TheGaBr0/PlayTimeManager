package me.thegabro.playtimemanager.Database.DatabaseTypes;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.PlayTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class PostgreSQLDatabase implements Database {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HikariDataSource dataSource;

    // Table creation statements
    private static final String PLAY_TIME_TABLE = "CREATE TABLE IF NOT EXISTS play_time (" +
            "uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "playtime BIGINT NOT NULL," +
            "artificial_playtime BIGINT NOT NULL," +
            "afk_playtime BIGINT NOT NULL," +
            "last_seen BIGINT DEFAULT NULL," +
            "first_join BIGINT DEFAULT NULL," +
            "relative_join_streak INT DEFAULT 0," +
            "absolute_join_streak INT DEFAULT 0," +
            "PRIMARY KEY (uuid)," +
            "UNIQUE (nickname)" +
            ");";

    private static final String RECEIVED_REWARDS_TABLE = "CREATE TABLE IF NOT EXISTS received_rewards (" +
            "id SERIAL PRIMARY KEY," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "received_at TEXT NOT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ");";

    private static final String COMPLETED_GOALS_TABLE = "CREATE TABLE IF NOT EXISTS completed_goals (" +
            "id SERIAL PRIMARY KEY," +
            "goal_name VARCHAR(36) NOT NULL," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "completed_at TEXT NOT NULL," +
            "received INTEGER NOT NULL DEFAULT 0," +
            "received_at TEXT DEFAULT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ");";

    private static final String REWARDS_TO_BE_CLAIMED_TABLE = "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
            "id SERIAL PRIMARY KEY," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "created_at TEXT NOT NULL," +
            "updated_at TEXT NOT NULL," +
            "expired INTEGER DEFAULT 0," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ");";

    @Override
    public void initialize() {
        FileConfiguration config = plugin.getConfig();

        String host = config.getString("postgresql.host", "localhost");
        int port = config.getInt("postgresql.port", 5432);
        String database = config.getString("postgresql.database", "playtime_manager");
        String username = config.getString("postgresql.username", "postgres");
        String password = config.getString("postgresql.password", "");
        String schema = config.getString("postgresql.schema", "public");
        boolean useSSL = config.getBoolean("postgresql.use-ssl", false);
        String connectionProperties = config.getString("postgresql.connection-properties", "");

        HikariConfig hikariConfig = new HikariConfig();

        // Build JDBC URL
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(host).append(":").append(port).append("/").append(database)
                .append("?currentSchema=").append(schema);

        if (useSSL) {
            jdbcUrl.append("&ssl=true&sslmode=require");
        }

        if (!connectionProperties.isEmpty()) {
            jdbcUrl.append("&").append(connectionProperties);
        }

        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("org.postgresql.Driver");

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(60000);
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // PostgreSQL specific optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        if (config.getBoolean("database-debug", false)) {
            plugin.getLogger().info("PostgreSQL JDBC URL: " + jdbcUrl.toString().replaceAll("password=[^&]*", "password=***"));
        }

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Successfully connected to PostgreSQL database");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize PostgreSQL connection", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("PostgreSQL initialization failed", e);
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
            plugin.getLogger().info("PostgreSQL connection closed");
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
            plugin.getLogger().log(Level.SEVERE, "Failed to create PostgreSQL tables", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("Failed to create PostgreSQL tables", e);
        }
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}