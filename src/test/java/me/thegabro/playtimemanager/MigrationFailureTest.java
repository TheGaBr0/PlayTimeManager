package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Migration.DatabaseMigration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A migration that cannot happen must leave everything as it was: the data, the database in use and
 * the config. None of these needs a real server: the target is unreachable, unsupported or the same.
 */
@Tag("migration")
class MigrationFailureTest extends PluginTestBase {

    private void givePlayersSomeData() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM play_time");
            s.execute("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime, afk_playtime) VALUES ('u-keep','Keeper',4242,1,2)");
            s.execute("DELETE FROM completed_goals");
            s.execute("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('kept','u-keep','Keeper','2026-01-01 00:00:00',1)");
        }
    }

    private void assertDataIsStillThere() throws Exception {
        assertEquals(4242, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime("u-keep"), "the player data is still there");
        assertEquals(1, DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals("u-keep").size());
    }

    /** Runs the check of the config the way the plugin does at startup, hiding the errors it logs on purpose. */
    private boolean migrateAsConfigured(String from, String to) throws Exception {
        var config = plugin.getConfiguration();
        config.set("database-type", from);
        config.set("migrating-to", to);
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            return quietly(() -> {
                try {
                    return new DatabaseMigration(plugin).checkAndExecuteMigration();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            System.setErr(realErr);
        }
    }

    private int backupsCreated() {
        File[] backups = new File(plugin.getDataFolder(), "backups").listFiles((dir, name) -> name.endsWith(".zip"));
        return backups == null ? 0 : backups.length;
    }

    @Test
    void withNothingScheduledNothingHappens() throws Exception {
        givePlayersSomeData();
        int before = backupsCreated();

        assertTrue(migrateAsConfigured("sqlite", "none"));

        assertEquals(before, backupsCreated(), "no backup either");
        assertDataIsStillThere();
    }

    @Test
    void anUnreachableMysqlLeavesTheServerOnItsDatabase() throws Exception {
        givePlayersSomeData();
        var config = plugin.getConfiguration();
        config.set("mysql.host", "127.0.0.1");
        config.set("mysql.port", 1); // nothing listens here
        config.set("mysql.username", "nobody");
        config.set("mysql.password", "wrong");
        int before = backupsCreated();

        boolean ok = migrateAsConfigured("sqlite", "mysql");

        assertFalse(ok, "the migration reports failure");
        assertEquals("sqlite", config.getString("database-type", "?"), "the server stays on the database it had");
        assertEquals("none", config.getString("migrating-to", "?"), "the schedule is cleared, it will not retry at every start");
        assertTrue(backupsCreated() > before, "a backup was made first");
        assertDataIsStillThere();
    }

    @Test
    void anUnreachablePostgresLeavesTheServerOnItsDatabase() throws Exception {
        givePlayersSomeData();
        var config = plugin.getConfiguration();
        config.set("postgresql.host", "127.0.0.1");
        config.set("postgresql.port", 1);
        config.set("postgresql.username", "nobody");
        config.set("postgresql.password", "wrong");

        assertFalse(migrateAsConfigured("sqlite", "postgresql"));

        assertEquals("sqlite", config.getString("database-type", "?"));
        assertEquals("none", config.getString("migrating-to", "?"));
        assertDataIsStillThere();
    }

    @Test
    void anUnsupportedDatabaseTypeIsRefused() throws Exception {
        givePlayersSomeData();

        assertFalse(migrateAsConfigured("sqlite", "oracle"));

        assertEquals("sqlite", plugin.getConfiguration().getString("database-type", "?"));
        assertEquals("none", plugin.getConfiguration().getString("migrating-to", "?"));
        assertDataIsStillThere();
    }

    @Test
    void ifTheBackupCannotBeMadeNothingIsMigrated() throws Exception {
        givePlayersSomeData();
        File backups = new File(plugin.getDataFolder(), "backups");
        deleteRecursively(backups);
        Files.writeString(backups.toPath(), "this is a file where the folder should be");

        boolean ok = migrateAsConfigured("sqlite", "mysql");

        assertFalse(ok, "without a backup the migration does not start");
        assertEquals("sqlite", plugin.getConfiguration().getString("database-type", "?"));
        assertEquals("none", plugin.getConfiguration().getString("migrating-to", "?"));
        assertDataIsStillThere();
        assertTrue(backups.delete()); // leave the folder free for the others
    }

    /**
     * Someone edits the config by hand and writes the database already in use as the target. The
     * migration drops the target's tables before copying: from and to being the same database, that
     * would erase everything. The command refuses this case; the config check must too.
     */
    @Test
    void migratingToTheDatabaseAlreadyInUseDoesNotEraseIt() throws Exception {
        givePlayersSomeData();

        migrateAsConfigured("sqlite", "sqlite");

        assertDataIsStillThere();
        assertEquals("none", plugin.getConfiguration().getString("migrating-to", "?"), "the schedule is cleared");
        assertEquals("sqlite", plugin.getConfiguration().getString("database-type", "?"));
    }

    private void deleteRecursively(File file) throws Exception {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
