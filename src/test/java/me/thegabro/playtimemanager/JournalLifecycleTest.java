package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** What keeps an open session accurate while the player is still online. */
@Tag("journal")
class JournalLifecycleTest extends PluginTestBase {
    /** The plugin writes to the database every 5 minutes = 6000 ticks. */
    private static final int PERIODIC_UPDATE_TICKS = 6001;

    @Test
    void thePeriodicUpdateExtendsTheOpenSessionAndItsAfkInterval() throws Exception {
        PlayerMock player = server.addPlayer("Steady");
        settle();
        String uuid = player.getUniqueId().toString();
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid);

        AFKSyncManager.getInstance().handleAFKGo(user);
        settle();
        long sessionEnd = dao.getSessions(uuid, 0, Long.MAX_VALUE).get(0).end();
        long afkEnd = dao.getAfkIntervals(uuid, 0, Long.MAX_VALUE).get(0).end();

        Thread.sleep(200);
        // Not settle(): that would run a heartbeat itself and hide what the periodic task does
        server.getScheduler().performTicks(PERIODIC_UPDATE_TICKS);
        Thread.sleep(500);

        assertTrue(dao.getSessions(uuid, 0, Long.MAX_VALUE).get(0).end() >= sessionEnd + 180,
                "the session end moved forward with the periodic update");
        assertTrue(dao.getAfkIntervals(uuid, 0, Long.MAX_VALUE).get(0).end() >= afkEnd + 180,
                "so did the AFK interval still in progress");
    }

    @Test
    void anOpenIntervalIsNeverLongerThanItsSession() throws Exception {
        PlayerMock player = server.addPlayer("Bounded");
        settle();
        String uuid = player.getUniqueId().toString();
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid);

        AFKSyncManager.getInstance().handleAFKGo(user);
        OnlineUsersManager.getInstance().addVanishedPlayer(user, true);
        Thread.sleep(80);
        settle(); // heartbeat

        var session = dao.getSessions(uuid, 0, Long.MAX_VALUE).get(0);
        var afk = dao.getAfkIntervals(uuid, 0, Long.MAX_VALUE).get(0);
        var vanish = dao.getVanishIntervals(uuid, 0, Long.MAX_VALUE).get(0);
        assertTrue(afk.start() >= session.start() && afk.end() <= session.end(), "AFK inside the session");
        assertTrue(vanish.start() >= session.start() && vanish.end() <= session.end(), "vanish inside the session");
        assertEquals(session.end(), afk.end(), "the heartbeat closes everything with the same timestamp");
        assertEquals(session.end(), vanish.end());
    }
}
