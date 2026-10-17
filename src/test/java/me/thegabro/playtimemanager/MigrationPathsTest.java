package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Migration.DatabaseMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the migration does with awkward data, and the way the plugin starts it, on a real MariaDB. */
@Tag("migration")
@Tag("database")
@RequiresDocker
class MigrationPathsTest extends PluginTestBase {

    @Override
    protected void customizeConfig(org.bukkit.configuration.file.YamlConfiguration config) {
        TestDatabases.configureMysql(config);
    }

    @Override
    protected void afterServerStopped() {
        TestDatabases.stopAll();
    }

    @BeforeEach
    void freshSource() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            // a previous test may have dropped one of them
            for (String ddl : me.thegabro.playtimemanager.Database.JournalTables.createStatements(
                    me.thegabro.playtimemanager.Database.Database.DBTYPES.SQLITE)) {
                s.execute(ddl);
            }
            for (String table : List.of("vanish_journal", "afk_journal", "playtime_journal", "rewards_to_be_claimed",
                    "received_rewards", "completed_goals", "play_time")) {
                s.execute("DELETE FROM " + table);
            }
            s.execute("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime, afk_playtime) VALUES ('u-1','Alice',500,0,0)");
        }
    }

    /** Connects the way the plugin does while it runs (see MySQLDatabase): dates are UTC on the server. */
    private Connection mariadb() throws Exception {
        var db = TestDatabases.mariadb();
        return DriverManager.getConnection("jdbc:mysql://" + db.getHost() + ":" + db.getMappedPort(3306) + "/"
                + db.getDatabaseName() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                db.getUsername(), db.getPassword());
    }

    private List<List<String>> targetRows(String query) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (Connection c = mariadb(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(query)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) row.add(String.valueOf(rs.getObject(i)));
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean migrateSqliteToMysql() throws Exception {
        Method run = DatabaseMigration.class.getDeclaredMethod("executeMigration", String.class, String.class);
        run.setAccessible(true);
        return (Boolean) run.invoke(new DatabaseMigration(plugin), "sqlite", "mysql");
    }

    @Test
    void theWayThePluginStartsItMovesTheServerToTheNewDatabase() throws Exception {
        var config = plugin.getConfiguration();
        config.set("database-type", "sqlite");
        config.set("migrating-to", "mysql");
        File backups = new File(plugin.getDataFolder(), "backups");
        File[] before = backups.listFiles();

        boolean ok = new DatabaseMigration(plugin).checkAndExecuteMigration();

        assertTrue(ok);
        assertEquals("mysql", config.getString("database-type", "?"), "the server will use the new database");
        assertEquals("none", config.getString("migrating-to", "?"), "and the schedule is cleared");
        assertEquals(List.of(List.of("Alice", "500")), targetRows("SELECT nickname, playtime FROM play_time"));
        assertTrue(backups.listFiles().length > (before == null ? 0 : before.length), "a backup was made before");
    }

    /**
     * The plugin writes and reads MySQL dates as UTC. The migration has to write them the same way,
     * whatever the time zone of the machine the server runs on, or every date shifts by its offset.
     */
    @Test
    void datesKeepTheirMomentWhenReadTheWayThePluginReadsThem() throws Exception {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo")); // UTC+9, no daylight saving
        try {
            try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
                s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) " +
                        "VALUES ('moment','u-1','Alice','2026-03-04 05:06:07',1,'2026-03-04 05:06:09')");
                s.execute("INSERT INTO rewards_to_be_claimed (user_uuid,nickname,main_instance_ID,required_joins,created_at,updated_at,expired) " +
                        "VALUES ('u-1','Alice',1,1,'2026-01-01 10:00:00','2026-01-02 11:00:00',0)");
            }

            assertTrue(migrateSqliteToMysql());

            try (Connection c = mariadb(); Statement s = c.createStatement()) {
                var goals = s.executeQuery("SELECT completed_at, received_at FROM completed_goals");
                assertTrue(goals.next());
                assertEquals(java.time.Instant.parse("2026-03-04T05:06:07Z"), goals.getTimestamp(1).toInstant());
                assertEquals(java.time.Instant.parse("2026-03-04T05:06:09Z"), goals.getTimestamp(2).toInstant());
                var rewards = s.executeQuery("SELECT created_at, updated_at FROM rewards_to_be_claimed");
                assertTrue(rewards.next());
                assertEquals(java.time.Instant.parse("2026-01-01T10:00:00Z"), rewards.getTimestamp(1).toInstant());
                assertEquals(java.time.Instant.parse("2026-01-02T11:00:00Z"), rewards.getTimestamp(2).toInstant());
            }
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Test
    void anUnreadableDateIsReplacedAndTheMigrationGoesOn() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('garbled','u-1','Alice','not a date',1)");
            s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('tform','u-1','Alice','2026-03-04T05:06:07',1)");
        }

        assertTrue(migrateSqliteToMysql(), "bad data does not stop the migration");

        List<List<String>> goals = targetRows("SELECT goal_name, completed_at FROM completed_goals ORDER BY goal_name");
        assertEquals(2, goals.size(), "both rows arrive");
        assertTrue(goals.get(0).get(1).startsWith(java.time.LocalDate.now().toString().substring(0, 4)),
                "the unreadable date became a current one: " + goals.get(0));
        assertTrue(goals.get(1).get(1).replace('T', ' ').startsWith("2026-03-04 05:06:07"),
                "the T separator is understood: " + goals.get(1));
    }

    @Test
    void aTableMissingInTheSourceIsCreatedEmptyInTheTarget() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE afk_journal");
        }

        assertTrue(migrateSqliteToMysql());

        assertEquals(List.of(List.of("0")), targetRows("SELECT COUNT(*) FROM afk_journal"), "it exists, empty");
        assertEquals(List.of(List.of("Alice")), targetRows("SELECT nickname FROM play_time"), "the others were copied");
    }

    @Test
    void moreRowsThanOneBatchAreAllCopied() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            s.execute("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 2500) " +
                    "INSERT INTO playtime_journal (uuid, login, logout) SELECT 'u-1', 1700000000000 + i, 1700000000000 + i + 10 FROM n");
        }

        assertTrue(migrateSqliteToMysql());

        assertEquals(List.of(List.of("2500")), targetRows("SELECT COUNT(*) FROM playtime_journal"),
                "the rows beyond the 1000 of a batch are not lost");
        assertEquals(List.of(List.of("1700000002500", "1700000002510")),
                targetRows("SELECT login, logout FROM playtime_journal ORDER BY login DESC LIMIT 1"));
    }
}
