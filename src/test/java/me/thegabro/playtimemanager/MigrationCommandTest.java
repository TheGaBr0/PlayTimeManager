package me.thegabro.playtimemanager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimemigration: schedules (or cancels) the move to another database at the next start. */
@Tag("migration")
class MigrationCommandTest extends PluginTestBase {

    @BeforeEach
    void nothingScheduled() {
        plugin.getConfiguration().set("database-type", "sqlite");
        plugin.getConfiguration().set("migrating-to", "none");
    }

    private String pending() {
        return plugin.getConfiguration().getString("migrating-to", "?");
    }

    private String ptm(String... args) throws Exception {
        return run(server.getConsoleSender(), "playtimemigration", args);
    }

    @Test
    void onlyTheConsoleCanUseIt() throws Exception {
        PlayerMock admin = server.addPlayer("MigrationAdmin");
        admin.setOp(true);
        settle();

        String out = run(admin, "playtimemigration", "start", "mysql");

        assertFalse(out.isBlank(), "the player is told it is console only");
        assertEquals("none", pending(), "and nothing is scheduled");
    }

    @Test
    void withoutArgumentsOrWithAnUnknownSubcommandItShowsTheUsage() throws Exception {
        for (String[] args : new String[][]{{}, {"explode"}}) {
            String out = ptm(args);
            assertTrue(out.contains("/playtimemigration start <type>"), out);
            assertTrue(out.contains("/playtimemigration cancel"), out);
            assertTrue(out.contains("mysql, mariadb, postgresql, sqlite"), out);
        }
    }

    @Test
    void startSchedulesTheMigration() throws Exception {
        String out = ptm("start", "postgresql");

        assertTrue(out.contains("Migration Scheduled"), out);
        assertTrue(out.contains("sqlite") && out.contains("postgresql"), "it says from where to where: " + out);
        assertEquals("postgresql", pending());
    }

    @Test
    void theTypeIsCaseInsensitive() throws Exception {
        ptm("start", "MariaDB");

        assertEquals("mariadb", pending());
    }

    @Test
    void startNeedsAValidTypeDifferentFromTheCurrentOne() throws Exception {
        assertTrue(ptm("start").contains("Usage"), "no type");
        assertEquals("none", pending());

        String invalid = ptm("start", "oracle");
        assertTrue(invalid.contains("Invalid database type"), invalid);
        assertEquals("none", pending());

        String same = ptm("start", "sqlite");
        assertTrue(same.contains("already using"), same);
        assertEquals("none", pending());
    }

    @Test
    void aSecondMigrationCannotBeScheduledOverThePending() throws Exception {
        ptm("start", "mysql");

        String out = ptm("start", "postgresql");

        assertTrue(out.contains("already a pending migration") && out.contains("mysql"), out);
        assertEquals("mysql", pending(), "the first one stays");
    }

    @Test
    void cancelClearsThePendingMigration() throws Exception {
        ptm("start", "mysql");

        String out = ptm("cancel");

        assertTrue(out.contains("Migration Cancelled") && out.contains("mysql"), out);
        assertEquals("none", pending());
    }

    @Test
    void cancelWithNothingPendingSaysSo() throws Exception {
        String out = ptm("cancel");

        assertTrue(out.contains("no pending migration"), out);
    }

    @Test
    void statusShowsWhatIsPendingOrThatNothingIs() throws Exception {
        String none = ptm("status");
        assertTrue(none.contains("Current database type: sqlite") && none.contains("No pending migrations"), none);

        ptm("start", "mysql");
        String scheduled = ptm("status");
        assertTrue(scheduled.contains("Pending migration: mysql"), scheduled);
        assertTrue(scheduled.contains("scheduled for the next reload/restart"), scheduled);
    }

    @Test
    void theConsoleGetsSuggestionsAndOthersNone() throws Exception {
        var command = server.getCommandMap().getCommand("playtimemigration");
        var console = server.getConsoleSender();

        assertEquals(List.of("start", "cancel", "status"), command.tabComplete(console, "ptmigration", new String[]{""}));
        assertEquals(List.of("start", "status"), command.tabComplete(console, "ptmigration", new String[]{"st"}));
        assertEquals(List.of("cancel"), command.tabComplete(console, "ptmigration", new String[]{"ca"}));
        assertEquals(List.of("mysql", "mariadb", "postgresql", "sqlite"),
                command.tabComplete(console, "ptmigration", new String[]{"start", ""}));
        assertEquals(List.of("postgresql"), command.tabComplete(console, "ptmigration", new String[]{"start", "po"}));
        assertTrue(command.tabComplete(console, "ptmigration", new String[]{"cancel", ""}).isEmpty());

        PlayerMock admin = server.addPlayer("MigrationTabAdmin");
        admin.setOp(true);
        settle();
        assertTrue(command.tabComplete(admin, "ptmigration", new String[]{""}).isEmpty(), "a player gets no suggestions");
    }
}
