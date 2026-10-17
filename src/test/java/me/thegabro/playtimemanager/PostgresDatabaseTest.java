package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.YamlConfiguration;

/** The main flows on a real PostgreSQL. */
class PostgresDatabaseTest extends DatabaseEndToEndTest {
    @Override
    protected void useDatabase(YamlConfiguration config) {
        TestDatabases.configurePostgres(config);
        config.set("database-type", "postgresql");
    }
}
