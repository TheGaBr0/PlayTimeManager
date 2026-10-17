package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real database servers started on demand with Docker, shared by the tests of one test class. */
final class TestDatabases {
    private static MariaDBContainer mariadb;
    private static PostgreSQLContainer postgres;

    private TestDatabases() {}

    /** False without Docker, or when -Dptm.skipDocker=true asks to leave the database tests out. */
    static boolean dockerAvailable() {
        if (Boolean.getBoolean("ptm.skipDocker")) {
            return false;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    static synchronized MariaDBContainer mariadb() {
        if (mariadb == null) {
            mariadb = new MariaDBContainer("mariadb:11.4").withDatabaseName("ptm");
            mariadb.start();
        }
        return mariadb;
    }

    static synchronized PostgreSQLContainer postgres() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("ptm");
            postgres.start();
        }
        return postgres;
    }

    /** Points the plugin's "mysql" settings at the MariaDB container. */
    static void configureMysql(YamlConfiguration config) {
        MariaDBContainer db = mariadb();
        config.set("mysql.host", db.getHost());
        config.set("mysql.port", db.getMappedPort(3306));
        config.set("mysql.database", db.getDatabaseName());
        config.set("mysql.username", db.getUsername());
        config.set("mysql.password", db.getPassword());
        config.set("mysql.use-ssl", false);
    }

    /** Points the plugin's "postgresql" settings at the PostgreSQL container. */
    static void configurePostgres(YamlConfiguration config) {
        PostgreSQLContainer db = postgres();
        config.set("postgresql.host", db.getHost());
        config.set("postgresql.port", db.getMappedPort(5432));
        config.set("postgresql.database", db.getDatabaseName());
        config.set("postgresql.username", db.getUsername());
        config.set("postgresql.password", db.getPassword());
        config.set("postgresql.use-ssl", false);
    }

    static synchronized void stopAll() {
        if (mariadb != null) {
            mariadb.stop();
            mariadb = null;
        }
        if (postgres != null) {
            postgres.stop();
            postgres = null;
        }
    }
}
