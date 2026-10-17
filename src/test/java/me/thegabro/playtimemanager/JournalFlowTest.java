package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("journal")
class JournalFlowTest extends PluginTestBase {

    @Test
    void joinAfkAndQuitAreJournaled() throws Exception {
        PlayerMock player = server.addPlayer("Steve");
        settle();

        String uuid = player.getUniqueId().toString();
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();

        List<JournalDAO.Interval> open = dao.getSessions(uuid, 0, Long.MAX_VALUE);
        assertEquals(1, open.size(), "one session after join");

        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid);
        assertNotNull(user, "the player is tracked as online");

        AFKSyncManager.getInstance().handleAFKGo(user);
        Thread.sleep(50);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        settle();

        assertEquals(1, dao.getAfkIntervals(uuid, 0, Long.MAX_VALUE).size(), "one AFK interval");

        player.disconnect();
        settle();

        JournalReport report = JournalReport.build(dao, uuid, 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions());
        assertTrue(report.afk() >= 40, "AFK lasted at least ~50ms, was " + report.afk());
        assertTrue(report.online() >= report.afk());
        assertEquals(0, report.vanish());
    }

    @Test
    void vanishIsJournaledInsideTheSession() throws Exception {
        PlayerMock player = server.addPlayer("Alex");
        settle();

        String uuid = player.getUniqueId().toString();
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();
        OnlineUsersManager users = OnlineUsersManager.getInstance();
        OnlineUser user = users.getOnlineUserByUUID(uuid);
        assertNotNull(user);

        users.addVanishedPlayer(user, true);
        Thread.sleep(60);
        AFKSyncManager.getInstance().handleAFKGo(user);   // AFK while vanished
        Thread.sleep(60);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        users.removeVanishedPlayer(user);
        settle();

        player.disconnect();
        settle();

        JournalReport report = JournalReport.build(dao, uuid, 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions(), "going vanish does not split the session");
        assertTrue(report.vanish() >= 100, "vanish lasted ~120ms, was " + report.vanish());
        assertTrue(report.afkWhileVanished() >= 50 && report.afkWhileVanished() == report.afk(),
                "the whole AFK happened while vanished: " + report.afkWhileVanished() + " / " + report.afk());
        assertEquals(report.online() - report.vanish(), report.onlineVisible());
    }
}
