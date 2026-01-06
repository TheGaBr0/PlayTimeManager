package me.thegabro.playtimemanager.Database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Database {

    enum DBTYPES {
        SQLITE,
        POSTGRESQL,
        MYSQL
    }

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
     * Get the database type
     * @return DBTYPES enum representing the database type
     */
    DBTYPES getDatabaseType();
}