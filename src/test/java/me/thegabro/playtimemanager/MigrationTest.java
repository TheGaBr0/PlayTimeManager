package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Migration.DatabaseMigration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The database migration in all six directions between SQLite, MySQL/MariaDB and PostgreSQL, journal
 * tables included. A known data set is copied around a full circle and compared with the original
 * after every step:
 * sqlite -> mysql -> postgresql -> sqlite -> postgresql -> mysql -> sqlite.
 */
@RequiresDocker
@Tag("migration")
@Tag("database")
class MigrationTest extends PluginTestBase {
    private static final DateTimeFormatter UTC_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> TABLES = List.of("play_time", "completed_goals", "received_rewards",
            "rewards_to_be_claimed", "playtime_journal", "afk_journal", "vanish_journal");

    private Map<String, List<List<String>>> original;

    @BeforeAll
    void prepare() throws Exception {
        var config = plugin.getConfiguration();
        TestDatabases.mariadb();
        TestDatabases.postgres();
        var mysql = TestDatabases.mariadb();
        config.set("mysql.host", mysql.getHost());
        config.set("mysql.port", mysql.getMappedPort(3306));
        config.set("mysql.database", mysql.getDatabaseName());
        config.set("mysql.username", mysql.getUsername());
        config.set("mysql.password", mysql.getPassword());
        config.set("mysql.use-ssl", false);
        var pg = TestDatabases.postgres();
        config.set("postgresql.host", pg.getHost());
        config.set("postgresql.port", pg.getMappedPort(5432));
        config.set("postgresql.database", pg.getDatabaseName());
        config.set("postgresql.username", pg.getUsername());
        config.set("postgresql.password", pg.getPassword());
        config.set("postgresql.use-ssl", false);
        config.set("postgresql.schema", "public");

        populateSqlite();
        original = dump("sqlite");
    }

    @Override
    protected void afterServerStopped() {
        TestDatabases.stopAll();
    }

    /** A little of everything, with the values that are easiest to get wrong. */
    private void populateSqlite() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String table : TABLES) {
                s.execute("DELETE FROM " + table);
            }
            s.execute("INSERT INTO play_time VALUES ('u-1','Alice',123456789012,72000,3600,1760000000000,1750000000000,3,17)");
            s.execute("INSERT INTO play_time VALUES ('u-2','Bob',0,0,0,NULL,NULL,0,0)");
            s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) " +
                    "VALUES ('first','u-1','Alice','2026-03-04 05:06:07',1,'2026-03-04 05:06:09')");
            s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) " +
                    "VALUES ('second','u-1','Alice','2026-12-31 23:59:59',0,NULL)");
            s.execute("INSERT INTO received_rewards (user_uuid,nickname,main_instance_ID,required_joins,received_at) " +
                    "VALUES ('u-1','Alice',7,3,'2026-06-01 00:00:00')");
            s.execute("INSERT INTO rewards_to_be_claimed (user_uuid,nickname,main_instance_ID,required_joins,created_at,updated_at,expired) " +
                    "VALUES ('u-2','Bob',9,1,'2026-01-01 10:00:00','2026-01-02 11:00:00',1)");
            s.execute("INSERT INTO playtime_journal VALUES ('u-1',1760000000123,1760000360456)");
            s.execute("INSERT INTO playtime_journal VALUES ('u-1',1760100000000,1760100000000)");
            s.execute("INSERT INTO afk_journal VALUES ('u-1',1760000000123,1760000100000,1760000200000)");
            s.execute("INSERT INTO vanish_journal VALUES ('u-1',1760000000123,1760000250000,1760000300000)");
        }
    }

    private Connection connect(String type) throws Exception {
        return switch (type) {
            case "sqlite" -> DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "play_time.db"));
            case "mysql" -> {
                var db = TestDatabases.mariadb();
                yield DriverManager.getConnection("jdbc:mysql://" + db.getHost() + ":" + db.getMappedPort(3306) + "/"
                        + db.getDatabaseName() + "?useSSL=false&allowPublicKeyRetrieval=true", db.getUsername(), db.getPassword());
            }
            case "postgresql" -> {
                var db = TestDatabases.postgres();
                yield DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getMappedPort(5432) + "/"
                        + db.getDatabaseName(), db.getUsername(), db.getPassword());
            }
            default -> throw new IllegalArgumentException(type);
        };
    }

    /** Every row of every table as text, with dates and numbers in one common spelling. */
    private Map<String, List<List<String>>> dump(String type) throws Exception {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        try (Connection c = connect(type); Statement s = c.createStatement()) {
            for (String table : TABLES) {
                List<List<String>> rows = new ArrayList<>();
                try (ResultSet rs = s.executeQuery("SELECT * FROM " + table)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    while (rs.next()) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            Object value = rs.getObject(i);
                            row.add(canonical(value));
                        }
                        rows.add(row);
                    }
                }
                rows.sort((a, b) -> String.join("|", a).compareTo(String.join("|", b)));
                result.put(table, rows);
            }
        }
        return result;
    }

    private String canonical(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZoneId.of("UTC")).format(UTC_TEXT);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return Timestamp.valueOf(ldt).toInstant().atZone(ZoneId.of("UTC")).format(UTC_TEXT);
        }
        if (value instanceof Number n) return String.valueOf(n.longValue());
        return value.toString();
    }

    private boolean migrate(String from, String to) throws Exception {
        DatabaseMigration migration = new DatabaseMigration(plugin);
        Method run = DatabaseMigration.class.getDeclaredMethod("executeMigration", String.class, String.class);
        run.setAccessible(true);
        return (Boolean) run.invoke(migration, from, to);
    }

    @TestFactory
    List<DynamicTest> aFullCircleKeepsEveryRow() {
        List<String> circle = List.of("sqlite", "mysql", "postgresql", "sqlite", "postgresql", "mysql", "sqlite");
        List<DynamicTest> steps = new ArrayList<>();
        for (int i = 0; i + 1 < circle.size(); i++) {
            String from = circle.get(i);
            String to = circle.get(i + 1);
            steps.add(DynamicTest.dynamicTest(from + " -> " + to, () -> {
                assertTrue(migrate(from, to), "the migration reports success");
                Map<String, List<List<String>>> migrated = dump(to);
                for (String table : TABLES) {
                    assertEquals(original.get(table), migrated.get(table),
                            "table " + table + " after " + from + " -> " + to);
                }
            }));
        }
        return steps;
    }
}
