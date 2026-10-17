package me.thegabro.playtimemanager;

import org.bukkit.configuration.file.YamlConfiguration;

/** The main flows on a real MariaDB (through the MySQL settings). */
class MariaDbDatabaseTest extends DatabaseEndToEndTest {
    @Override
    protected void useDatabase(YamlConfiguration config) {
        TestDatabases.configureMysql(config);
        config.set("database-type", "mysql");
    }
}
