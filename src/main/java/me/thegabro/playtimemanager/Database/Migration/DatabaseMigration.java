package me.thegabro.playtimemanager.Database.Migration;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Database.DatabaseBackupUtility;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseMigration {

    private final PlayTimeManager plugin;
    private final Configuration config;
    private final Logger logger;
    private final DatabaseBackupUtility backupUtility = DatabaseBackupUtility.getInstance();

    private Connection sourceConnection;
    private Connection targetConnection;

    // Standard ISO format for all databases
    private static final DateTimeFormatter STANDARD_ISO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DatabaseMigration(PlayTimeManager plugin) {
        this.plugin = plugin;
        this.config = Configuration.getInstance();
        this.logger = plugin.getLogger();
    }

    public boolean checkAndExecuteMigration() {
        String migratingTo = config.getString("migrating-to", "none");

        if (migratingTo.equalsIgnoreCase("none")) {
            return true;
        }

        String currentDbType = config.getString("database-type", "sqlite");

        logger.info("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        logger.info("MIGRATION IN PROGRESS");
        logger.info("Source: " + currentDbType.toUpperCase());
        logger.info("Target: " + migratingTo.toUpperCase());
        logger.info("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        logger.info("Creating backup of the current PlayTimeManager instance...");
        File backupSuccess = backupUtility.createBackup("Database migration from "+ currentDbType.toUpperCase() + " to "+ migratingTo.toUpperCase());
        if (backupSuccess != null) {
            logger.info("✓ Backup created successfully!");
        } else {
            logger.severe("✗ Migration FAILED!");
            logger.severe("✗ The plugin will continue using the source database");
            config.set("migrating-to", "none");
            logger.info("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        boolean success = executeMigration(currentDbType, migratingTo);

        if (success) {
            logger.info("✓ Migration completed successfully!");
            config.set("database-type", migratingTo);
            config.set("migrating-to", "none");
            logger.info("✓ Configuration updated");
        } else {
            logger.severe("✗ Migration FAILED!");
            logger.severe("✗ The plugin will continue using the source database");
            config.set("migrating-to", "none");
        }

        logger.info("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        return success;
    }

    private boolean executeMigration(String sourceType, String targetType) {

        try {
            logger.info("Step 1/5: Connecting to source database (" + sourceType + ")...");
            sourceConnection = createConnection(sourceType);
            if (sourceConnection == null) {
                logger.severe("Failed to connect to source database!");
                return false;
            }
            logger.info("✓ Connected to source database");

            logger.info("Step 2/5: Connecting to target database (" + targetType + ")...");
            targetConnection = createConnection(targetType);
            if (targetConnection == null) {
                logger.severe("Failed to connect to target database!");
                closeConnection(sourceConnection);
                return false;
            }
            logger.info("✓ Connected to target database");

            logger.info("Step 3/5: Dropping existing tables in target database...");
            if (!dropTargetTables()) {
                logger.severe("Failed to drop existing tables!");
                closeConnections();
                return false;
            }
            logger.info("✓ Existing tables dropped");

            logger.info("Step 4/5: Creating tables in target database...");
            if (!createTargetTables(targetType)) {
                logger.severe("Failed to create tables in target database!");
                closeConnections();
                return false;
            }
            logger.info("✓ Target database tables created");

            logger.info("Step 5/5: Migrating data...");
            if (!migrateAllData()) {
                logger.severe("Data migration failed!");
                closeConnections();
                return false;
            }
            logger.info("✓ Data migrated successfully");

            closeConnections();
            return true;

        } catch (Exception e) {
            logger.severe("Migration error: " + e.getMessage());
            e.printStackTrace();
            closeConnections();
            return false;
        }
    }

    private Connection createConnection(String dbType) {
        try {
            loadDriver(dbType);

            switch (dbType.toLowerCase()) {
                case "sqlite":
                    return createSQLiteConnection();
                case "mysql":
                case "mariadb":
                    return createMySQLConnection();
                case "postgresql":
                    return createPostgreSQLConnection();
                default:
                    logger.severe("Unsupported database type: " + dbType);
                    return null;
            }
        } catch (Exception e) {
            logger.severe("Failed to create connection to " + dbType + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void loadDriver(String dbType) throws ClassNotFoundException {
        switch (dbType.toLowerCase()) {
            case "sqlite":
                Class.forName("org.sqlite.JDBC");
                break;
            case "mysql":
            case "mariadb":
                Class.forName("com.mysql.cj.jdbc.Driver");
                break;
            case "postgresql":
                Class.forName("org.postgresql.Driver");
                break;
        }
    }

    private Connection createSQLiteConnection() throws SQLException {
        String path = plugin.getDataFolder() + "/play_time.db";
        String url = "jdbc:sqlite:" + path;
        return DriverManager.getConnection(url);
    }

    private Connection createMySQLConnection() throws SQLException {
        String host = config.getString("mysql.host", "localhost");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "playtime_manager");
        String username = config.getString("mysql.username", null);
        String password = config.getString("mysql.password", null);
        boolean useSSL = config.getBoolean("mysql.use-ssl", false);
        String properties = config.getString("mysql.connection-properties", "");

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true",
                host, port, database, useSSL);

        if (properties != null && !properties.isEmpty()) {
            url += "&" + properties;
        }

        return DriverManager.getConnection(url, username, password);
    }

    private Connection createPostgreSQLConnection() throws SQLException {
        String host = config.getString("postgresql.host", "localhost");
        int port = config.getInt("postgresql.port", 5432);
        String database = config.getString("postgresql.database", "playtime_manager");
        String username = config.getString("postgresql.username", null);
        String password = config.getString("postgresql.password", null);
        String schema = config.getString("postgresql.schema", "public");
        if (schema == null || schema.isEmpty()) {
            schema = "public";
        }
        boolean useSSL = config.getBoolean("postgresql.use-ssl", false);
        String properties = config.getString("postgresql.connection-properties", "");

        StringBuilder url = new StringBuilder("jdbc:postgresql://")
                .append(host).append(":").append(port).append("/").append(database)
                .append("?currentSchema=").append(schema);

        if (useSSL) {
            url.append("&ssl=true&sslmode=require");
        }

        if (properties != null && !properties.isEmpty()) {
            url.append("&").append(properties);
        }

        logger.info("PostgreSQL Connection URL: " + url);
        logger.info("PostgreSQL Username: " + username);

        return DriverManager.getConnection(url.toString(), username, password);
    }

    private boolean dropTargetTables() {
        List<String> tables = getRequiredTables();

        try (Statement stmt = targetConnection.createStatement()) {
            for (int i = tables.size() - 1; i >= 0; i--) {
                String tableName = tables.get(i);
                try {
                    stmt.execute("DROP TABLE IF EXISTS " + tableName);
                    logger.info("  ✓ Dropped table: " + tableName);
                } catch (SQLException e) {
                    logger.warning("  ⚠ Could not drop table " + tableName + ": " + e.getMessage());
                }
            }
            return true;
        } catch (SQLException e) {
            logger.severe("Error dropping tables: " + e.getMessage());
            return false;
        }
    }

    private boolean createTargetTables(String targetType) {
        try {
            List<String> createStatements = getCreateTableStatements(targetType);

            for (String statement : createStatements) {
                try (Statement stmt = targetConnection.createStatement()) {
                    stmt.execute(statement);
                }
            }
            return true;
        } catch (SQLException e) {
            logger.severe("Error creating target tables: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean migrateAllData() {
        List<String> tables = getRequiredTables();
        int totalRecords = 0;

        for (String tableName : tables) {
            try {
                int recordCount = migrateTable(tableName);
                if (recordCount >= 0) {
                    totalRecords += recordCount;
                    logger.info("  ✓ Migrated " + recordCount + " records from " + tableName);
                } else {
                    logger.warning("  ⚠ Table " + tableName + " not found in source - skipping");
                }
            } catch (SQLException e) {
                logger.severe("  ✗ Failed to migrate table " + tableName + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        logger.info("Total records migrated: " + totalRecords);
        return true;
    }

    private int migrateTable(String tableName) throws SQLException {
        DatabaseMetaData metaData = sourceConnection.getMetaData();
        ResultSet rs = metaData.getTables(null, null, tableName, null);
        if (!rs.next()) {
            rs = metaData.getTables(null, null, tableName.toUpperCase(), null);
            if (!rs.next()) {
                rs.close();
                return -1;
            }
        }
        rs.close();

        String selectQuery = "SELECT * FROM " + tableName;
        List<Object[]> rows = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        List<Integer> columnTypes = new ArrayList<>();
        int columnCount = 0;

        try (Statement stmt = sourceConnection.createStatement();
             ResultSet resultSet = stmt.executeQuery(selectQuery)) {

            ResultSetMetaData rsMetaData = resultSet.getMetaData();
            columnCount = rsMetaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(rsMetaData.getColumnName(i));
                columnTypes.add(rsMetaData.getColumnType(i));
            }

            while (resultSet.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = resultSet.getObject(i);
                }
                rows.add(row);
            }
        }

        List<Integer> targetColumnTypes = new ArrayList<>();
        try (Statement stmt = targetConnection.createStatement();
             ResultSet rs2 = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 0")) {
            ResultSetMetaData targetMeta = rs2.getMetaData();
            for (int i = 1; i <= columnCount; i++) {
                targetColumnTypes.add(targetMeta.getColumnType(i));
            }
        }

        if (!rows.isEmpty()) {
            StringBuilder insertQuery = new StringBuilder("INSERT INTO " + tableName + " (");
            for (int i = 0; i < columnNames.size(); i++) {
                insertQuery.append(columnNames.get(i));
                if (i < columnNames.size() - 1) {
                    insertQuery.append(", ");
                }
            }
            insertQuery.append(") VALUES (");
            for (int i = 0; i < columnCount; i++) {
                insertQuery.append("?");
                if (i < columnCount - 1) {
                    insertQuery.append(", ");
                }
            }
            insertQuery.append(")");

            try (PreparedStatement pstmt = targetConnection.prepareStatement(insertQuery.toString())) {
                targetConnection.setAutoCommit(false);

                int batchSize = 0;
                for (Object[] row : rows) {
                    for (int i = 0; i < columnCount; i++) {
                        Object value = row[i];
                        String columnName = columnNames.get(i);
                        int targetType = targetColumnTypes.get(i);  // Keep for non-datetime types

                        // Convert datetime fields to space-separated ISO string for all targets
                        boolean isDateTimeColumn = columnName.equals("completed_at") ||
                                columnName.equals("received_at") ||
                                columnName.equals("created_at") ||
                                columnName.equals("updated_at");

                        if (isDateTimeColumn) {
                            Timestamp ts = null;

                            if (value != null) {
                                try {
                                    if (value instanceof String strValue) {
                                        ts = parseISOToTimestamp(strValue);
                                    } else if (value instanceof Timestamp tsValue) {
                                        ts = tsValue;
                                    } else if (value instanceof java.util.Date dateValue) {
                                        ts = new Timestamp(dateValue.getTime());
                                    } else if (value instanceof java.time.LocalDateTime ldtValue) {
                                        ts = Timestamp.valueOf(ldtValue);
                                    } else if (value instanceof java.time.Instant instantValue) {
                                        ts = Timestamp.from(instantValue);
                                    } else {
                                        throw new SQLException("Unsupported datetime type: " + value.getClass());
                                    }
                                } catch (Exception e) {
                                    logger.warning("Failed to parse datetime in " + columnName + ": " + value + " -> " + e.getMessage());
                                    // don't set ts = null - use current time as fallback for not null attributes
                                    ts = new Timestamp(System.currentTimeMillis());
                                }
                            } else {
                                // value is null but attribute is not null → use current time
                                ts = new Timestamp(System.currentTimeMillis());
                            }

                            if (targetType == Types.TIMESTAMP || targetType == Types.DATE || targetType == Types.TIME) {
                                pstmt.setTimestamp(i + 1, ts);
                            } else {
                                pstmt.setString(i + 1, formatTimestampToISO(ts));  // Always space format
                            }
                        } else {
                            pstmt.setObject(i + 1, value);
                        }
                    }
                    pstmt.addBatch();
                    batchSize++;

                    if (batchSize % 1000 == 0) {
                        pstmt.executeBatch();
                        targetConnection.commit();
                        batchSize = 0;
                    }
                }

                if (batchSize > 0) {
                    pstmt.executeBatch();
                    targetConnection.commit();
                }

                targetConnection.setAutoCommit(true);
            } catch (SQLException e) {
                targetConnection.rollback();
                targetConnection.setAutoCommit(true);
                throw e;
            }
        }

        return rows.size();
    }

    private Timestamp parseISOToTimestamp(String text) throws SQLException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            // Normalize the input by replacing 'T' with space if present
            String normalized = text.replace('T', ' ');

            LocalDateTime ldt = LocalDateTime.parse(normalized, STANDARD_ISO_FORMAT);
            Instant instant = ldt.atZone(ZoneId.of("UTC")).toInstant();
            return Timestamp.from(instant);
        } catch (DateTimeParseException e) {
            throw new SQLException(
                    "Invalid datetime format: '" + text +
                            "'. Expected yyyy-MM-dd HH:mm:ss or yyyy-MM-dd'T'HH:mm:ss (UTC)",
                    e
            );
        }
    }

    private String formatTimestampToISO(Timestamp timestamp) {
        return timestamp
                .toInstant()
                .atZone(ZoneId.of("UTC"))
                .toLocalDateTime()
                .format(STANDARD_ISO_FORMAT);
    }

    private List<String> getRequiredTables() {
        List<String> tables = new ArrayList<>();
        tables.add("play_time");
        tables.add("completed_goals");
        tables.add("received_rewards");
        tables.add("rewards_to_be_claimed");
        return tables;
    }

    private List<String> getCreateTableStatements(String dbType) {
        List<String> statements = new ArrayList<>();

        switch (dbType.toLowerCase()) {
            case "sqlite":
                statements.add(
                        "CREATE TABLE IF NOT EXISTS play_time (" +
                                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                                "nickname VARCHAR(36) NOT NULL UNIQUE, " +
                                "playtime BIGINT NOT NULL, " +
                                "artificial_playtime BIGINT NOT NULL, " +
                                "afk_playtime BIGINT NOT NULL, " +
                                "last_seen BIGINT DEFAULT NULL, " +
                                "first_join BIGINT DEFAULT NULL, " +
                                "relative_join_streak INT DEFAULT 0, " +
                                "absolute_join_streak INT DEFAULT 0, " +
                                "PRIMARY KEY (uuid))"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS completed_goals (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "goal_name VARCHAR(36) NOT NULL, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "completed_at TEXT NOT NULL, " +
                                "received INTEGER NOT NULL DEFAULT 0, " +
                                "received_at TEXT DEFAULT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid))"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "created_at TEXT NOT NULL, " +
                                "updated_at TEXT NOT NULL, " +
                                "expired INTEGER DEFAULT 0, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid))"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS received_rewards (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "received_at TEXT NOT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid))"
                );
                break;

            case "mysql":
            case "mariadb":
                statements.add(
                        "CREATE TABLE IF NOT EXISTS play_time (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "playtime BIGINT NOT NULL, " +
                                "artificial_playtime BIGINT NOT NULL, " +
                                "afk_playtime BIGINT NOT NULL, " +
                                "last_seen BIGINT DEFAULT NULL, " +
                                "first_join BIGINT DEFAULT NULL, " +
                                "relative_join_streak INT DEFAULT 0, " +
                                "absolute_join_streak INT DEFAULT 0, " +
                                "PRIMARY KEY (uuid), " +
                                "UNIQUE KEY unique_nickname (nickname)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS completed_goals (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "goal_name VARCHAR(36) NOT NULL, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "completed_at DATETIME NOT NULL, " +
                                "received INTEGER NOT NULL DEFAULT 0, " +
                                "received_at DATETIME DEFAULT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "created_at DATETIME NOT NULL, " +
                                "updated_at DATETIME NOT NULL, " +
                                "expired INTEGER DEFAULT 0, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS received_rewards (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "received_at DATETIME NOT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                break;

            case "postgresql":
                statements.add(
                        "CREATE TABLE IF NOT EXISTS play_time (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "playtime BIGINT NOT NULL, " +
                                "artificial_playtime BIGINT NOT NULL, " +
                                "afk_playtime BIGINT NOT NULL, " +
                                "last_seen BIGINT DEFAULT NULL, " +
                                "first_join BIGINT DEFAULT NULL, " +
                                "relative_join_streak INT DEFAULT 0, " +
                                "absolute_join_streak INT DEFAULT 0, " +
                                "PRIMARY KEY (uuid), " +
                                "UNIQUE (nickname))"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS completed_goals (" +
                                "id SERIAL PRIMARY KEY, " +
                                "goal_name VARCHAR(36) NOT NULL, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "completed_at TIMESTAMP NOT NULL, " +
                                "received INTEGER NOT NULL DEFAULT 0, " +
                                "received_at TIMESTAMP DEFAULT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE)"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS rewards_to_be_claimed (" +
                                "id SERIAL PRIMARY KEY, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "created_at TIMESTAMP NOT NULL, " +
                                "updated_at TIMESTAMP NOT NULL, " +
                                "expired INTEGER DEFAULT 0, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE)"
                );
                statements.add(
                        "CREATE TABLE IF NOT EXISTS received_rewards (" +
                                "id SERIAL PRIMARY KEY, " +
                                "user_uuid VARCHAR(36) NOT NULL, " +
                                "nickname VARCHAR(36) NOT NULL, " +
                                "main_instance_ID INT NOT NULL, " +
                                "required_joins INT NOT NULL, " +
                                "received_at TIMESTAMP NOT NULL, " +
                                "FOREIGN KEY (user_uuid) REFERENCES play_time(uuid) ON DELETE CASCADE)"
                );
                break;
        }

        return statements;
    }

    private void closeConnections() {
        closeConnection(sourceConnection);
        closeConnection(targetConnection);
    }

    private void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warning("Error closing connection: " + e.getMessage());
            }
        }
    }
}