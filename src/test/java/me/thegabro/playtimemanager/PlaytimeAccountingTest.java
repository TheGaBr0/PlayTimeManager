package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.PlayerDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How played time reaches the database. The time comes from the server statistic PLAY_ONE_MINUTE,
 * which the tests set by hand (20 ticks = 1 second).
 */
@Tag("accounting")
class PlaytimeAccountingTest extends PluginTestBase {

    private PlayerDAO players() {
        return DatabaseHandler.getInstance().getPlayerDAO();
    }

    private PlayerMock join(String name, UUID uuid) throws Exception {
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        return player;
    }

    @Test
    void quittingSavesThePlayedTime() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = join("Saver", uuid);

        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        player.disconnect();
        settle();

        assertEquals(1200, players().getPlaytime(uuid.toString()));
    }

    @Test
    void playtimeAccumulatesAcrossSessions() throws Exception {
        UUID uuid = UUID.randomUUID();

        PlayerMock first = join("Returning", uuid);
        first.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        first.disconnect();
        settle();

        // A new connection: the server statistic is read again, the database keeps the total
        PlayerMock second = join("Returning", uuid);
        second.setStatistic(Statistic.PLAY_ONE_MINUTE, 600);
        second.disconnect();
        settle();

        assertEquals(1800, players().getPlaytime(uuid.toString()));
    }

    @Test
    void thePeriodicUpdateSavesOnlinePlayersToo() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = join("StillHere", uuid);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 700);

        server.getScheduler().performTicks(6001); // every 5 minutes
        Thread.sleep(500);

        assertEquals(700, players().getPlaytime(uuid.toString()), "saved without waiting for the quit");
    }

    @Test
    void afkTimeIsCountedOnlyWhileAfk() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = join("Napper", uuid);
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());

        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 100);
        AFKSyncManager.getInstance().handleAFKGo(user);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 400);   // 300 ticks AFK
        AFKSyncManager.getInstance().handleAFKReturn(user);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 500);   // 100 more, awake
        player.disconnect();
        settle();

        assertEquals(500, players().getPlaytime(uuid.toString()));
        assertEquals(300, players().getAFKPlaytime(uuid.toString()));
    }

    @Test
    void leavingWhileAfkCountsTheAfkTimeUntilTheQuit() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = join("Sleeper", uuid);
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());

        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 100);
        AFKSyncManager.getInstance().handleAFKGo(user);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1000);
        player.disconnect();
        settle();

        assertEquals(900, players().getAFKPlaytime(uuid.toString()));
    }

    @Test
    void firstJoinIsKeptAndLastSeenMovesForward() throws Exception {
        UUID uuid = UUID.randomUUID();
        Instant before = Instant.now();

        PlayerMock first = join("Datekeeper", uuid);
        first.disconnect();
        settle();
        Instant firstJoin = players().getFirstJoin(uuid.toString());
        Instant firstSeen = players().getLastSeen(uuid.toString());
        assertNotNull(firstJoin);
        assertNotNull(firstSeen);
        assertFalse(firstJoin.isBefore(before.minusSeconds(1)));

        Thread.sleep(1100);
        PlayerMock second = join("Datekeeper", uuid);
        second.disconnect();
        settle();

        assertEquals(firstJoin, players().getFirstJoin(uuid.toString()), "first join never changes");
        assertTrue(players().getLastSeen(uuid.toString()).isAfter(firstSeen), "last seen moves forward");
    }

    @Test
    void playersAreIndependent() throws Exception {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        PlayerMock a = join("Alpha", aId);
        PlayerMock b = join("Bravo", bId);

        a.setStatistic(Statistic.PLAY_ONE_MINUTE, 300);
        b.setStatistic(Statistic.PLAY_ONE_MINUTE, 900);
        a.disconnect();
        b.disconnect();
        settle();

        assertEquals(300, players().getPlaytime(aId.toString()));
        assertEquals(900, players().getPlaytime(bId.toString()));
    }
}
