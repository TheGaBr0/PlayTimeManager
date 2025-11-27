package me.thegabro.playtimemanager.Database;

import me.thegabro.playtimemanager.Database.DatabaseTypes.MySQLDatabase;
import me.thegabro.playtimemanager.Database.DatabaseTypes.PostgreSQLDatabase;
import me.thegabro.playtimemanager.Database.DatabaseTypes.SQLiteDatabase;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.logging.Level;

public class DatabaseFactory {
    private static final PlayTimeManager plugin = PlayTimeManager.getInstance();

    /**
     * Create a database instance based on configuration
     * @return Database implementation
     */
    public static Database createDatabase() {
        String databaseType = plugin.getConfiguration().getString("database-type");

        if(databaseType == null){
            databaseType = "sqlite";
        }

        try {
            return switch (databaseType) {
                case "mysql", "mariadb" -> new MySQLDatabase();
                case "postgresql", "postgres" -> new PostgreSQLDatabase();
                default -> {
                    if (!databaseType.equals("sqlite")) {
                        plugin.getLogger().log(Level.WARNING,
                                "Unknown database type '" + databaseType + "', defaulting to SQLite");
                    }
                    yield new SQLiteDatabase();
                }
            };
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to initialize " + databaseType + " database, falling back to SQLite", e);
            return new SQLiteDatabase();
        }
    }
}