package me.thegabro.playtimemanager.Database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Database {
    /**
     * Initialize the database connection
     */
    void initialize();

    /**
     * Get a connection to the database
     * @return Connection object
     * @throws SQLException if connection fails
     */
    Connection getConnection() throws SQLException;

    /**
     * Close the database connection and cleanup resources
     */
    void close();

    /**
     * Create all necessary tables for the plugin
     */
    void createTables();

    /**
     * Get the database type (for logging/debugging purposes)
     * @return String representing the database type
     */
    String getDatabaseType();
}