package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A player that comes back with the same name and a different UUID (e.g. a server switching between
 * offline and online mode) keeps their data: the plugin moves it to the new UUID.
 */
@Tag("journal")
class UuidChangeTest extends PluginTestBase {

    @Test
    void journalFollowsThePlayerToTheNewUuid() throws Exception {
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        JournalDAO journal = DatabaseHandler.getInstance().getJournalDAO();

        // First life, under the old UUID: one session with an AFK interval
        PlayerMock before = new PlayerMock(server, "Shifty", oldUuid);
        server.addPlayer(before);
        settle();
        AFKSyncManager.getInstance().handleAFKGo(OnlineUsersManager.getInstance().getOnlineUserByUUID(oldUuid.toString()));
        Thread.sleep(60);
        before.disconnect();
        settle();
        assertEquals(1, journal.getSessions(oldUuid.toString(), 0, Long.MAX_VALUE).size());
        assertEquals(1, journal.getAfkIntervals(oldUuid.toString(), 0, Long.MAX_VALUE).size());

        // Second life: same name, new UUID
        PlayerMock after = new PlayerMock(server, "Shifty", newUuid);
        server.addPlayer(after);
        settle();

        assertEquals(0, journal.getSessions(oldUuid.toString(), 0, Long.MAX_VALUE).size(),
                "nothing is left under the old UUID");
        assertEquals(0, journal.getAfkIntervals(oldUuid.toString(), 0, Long.MAX_VALUE).size());
        assertEquals(2, journal.getSessions(newUuid.toString(), 0, Long.MAX_VALUE).size(),
                "the old session plus the one just opened");
        assertEquals(1, journal.getAfkIntervals(newUuid.toString(), 0, Long.MAX_VALUE).size(),
                "the AFK interval of the first life moved too");

        var players = DatabaseHandler.getInstance().getPlayerDAO();
        assertTrue(players.playerExists(newUuid.toString()));
        assertFalse(players.playerExists(oldUuid.toString()));
    }
}
