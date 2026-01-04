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

public class MySQLDatabase implements Database {
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private HikariDataSource dataSource;

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
            "UNIQUE KEY unique_nickname (nickname)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String RECEIVED_REWARDS_TABLE = "CREATE TABLE IF NOT EXISTS received_rewards (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String COMPLETED_GOALS_TABLE = "CREATE TABLE IF NOT EXISTS completed_goals (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "goal_name VARCHAR(36) NOT NULL," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "received INTEGER NOT NULL DEFAULT 0," +
            "received_at TIMESTAMP NULL DEFAULT NULL," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    private static final String REWARDS_TO_BE_CLAIMED_TABLE = "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "user_uuid VARCHAR(36) NOT NULL," +
            "nickname VARCHAR(36) NOT NULL," +
            "main_instance_ID INT NOT NULL," +
            "required_joins INT NOT NULL," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "expired BOOLEAN DEFAULT FALSE," +
            "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

    @Override
    public void initialize() {
        FileConfiguration config = plugin.getConfig();

        String host = config.getString("mysql.host", "localhost");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "playtime_manager");
        String username = config.getString("mysql.username", "root");
        String password = config.getString("mysql.password", "");
        boolean useSSL = config.getBoolean("mysql.use-ssl", false);
        String connectionProperties = config.getString("mysql.connection-properties", "");

        HikariConfig hikariConfig = new HikariConfig();

        StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://")
                .append(host).append(":").append(port).append("/").append(database)
                .append("?useSSL=").append(useSSL)
                .append("&allowPublicKeyRetrieval=true")
                .append("&serverTimezone=UTC")
                .append("&useUnicode=true")
                .append("&characterEncoding=utf8");

        if (!connectionProperties.isEmpty()) {
            jdbcUrl.append("&").append(connectionProperties);
        }

        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setLeakDetectionThreshold(60000);
        hikariConfig.setConnectionTestQuery("SELECT 1");

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        if (config.getBoolean("database-debug", false)) {
            plugin.getLogger().info("MySQL JDBC URL: " + jdbcUrl.toString().replaceAll("password=[^&]*", "password=***"));
        }

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Successfully connected to MySQL database");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL connection", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("MySQL initialization failed", e);
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
            plugin.getLogger().info("MySQL connection closed");
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
            plugin.getLogger().log(Level.SEVERE, "Failed to create MySQL tables", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException("Failed to create MySQL tables", e);
        }
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
}