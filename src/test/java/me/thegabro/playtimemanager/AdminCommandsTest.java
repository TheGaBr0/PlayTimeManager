package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimereload and /playtimebackup. */
@Tag("commands")
class AdminCommandsTest extends PluginTestBase {

    @Test
    void reloadKeepsOnlinePlayersTrackedAndTheirJournalSession() throws Exception {
        PlayerMock player = server.addPlayer("Reloaded");
        settle();
        String uuid = player.getUniqueId().toString();
        ConsoleCommandSenderMock console = server.getConsoleSender();

        String out = run(console, "playtimereload");
        assertTrue(out.contains("reloaded"), out);
        settle();

        assertNotNull(OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid),
                "the player is loaded again after the reload");

        player.disconnect();
        settle();
        JournalReport report = JournalReport.build(
                DatabaseHandler.getInstance().getJournalDAO(), uuid, 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions(), "the reload does not split the journal session");
    }

    @Test
    void backupIsCreated() throws Exception {
        String out = run(server.getConsoleSender(), "playtimebackup");
        assertTrue(out.contains("Database backup created successfully"), out);
    }
}
