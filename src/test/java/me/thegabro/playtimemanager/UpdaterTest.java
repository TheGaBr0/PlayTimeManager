package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Updates.UpdateManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Upgrading a database that does not have the journal tables yet. */
@Tag("updater")
class UpdaterTest extends PluginTestBase {

    private void dropJournalTables() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String table : List.of("playtime_journal", "afk_journal", "vanish_journal")) {
                s.execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    private void assertJournalWorks() {
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();
        dao.executeBatch(List.of(
                JournalDAO.insertSession("u", 1000),
                JournalDAO.insertAfk("u", 1000, 1200),
                JournalDAO.updateAfkEnd("u", 1200, 1500),
                JournalDAO.insertVanish("u", 1000, 1300),
                JournalDAO.updateVanishEnd("u", 1300, 1400),
                JournalDAO.updateSessionLogout("u", 1000, 2000)));

        JournalReport report = JournalReport.build(dao, "u", 0, 10_000);
        assertEquals(1, report.sessions());
        assertEquals(1000, report.online());
        assertEquals(300, report.afk());
        assertEquals(100, report.vanish());
    }

    @Test
    void fromConfigVersion45() throws Exception {
        dropJournalTables();
        assertTrue(UpdateManager.getInstance().performVersionUpdate("4.5", "4.6"));
        assertJournalWorks();
    }

    @Test
    void fromConfigVersion44() throws Exception {
        dropJournalTables();
        assertTrue(UpdateManager.getInstance().performVersionUpdate("4.4", "4.6"));
        assertJournalWorks();
    }

    @Test
    void runningTheUpdaterOnADatabaseThatAlreadyHasTheTablesIsHarmless() throws Exception {
        DatabaseHandler.getInstance().getJournalDAO().executeBatch(List.of(JournalDAO.insertSession("keep", 5000)));

        assertTrue(UpdateManager.getInstance().performVersionUpdate("4.5", "4.6"));

        assertEquals(1, DatabaseHandler.getInstance().getJournalDAO().getSessions("keep", 0, Long.MAX_VALUE).size(),
                "existing rows survive");
    }

    @Test
    void unsupportedVersionsAreRefused() {
        // Both are logged as errors by the plugin, which is the point of the test
        assertFalse(quietly(() -> UpdateManager.getInstance().performVersionUpdate("3.1", "4.6")));
        assertFalse(quietly(() -> UpdateManager.getInstance().performVersionUpdate("9.9", "4.6")));
    }
}
