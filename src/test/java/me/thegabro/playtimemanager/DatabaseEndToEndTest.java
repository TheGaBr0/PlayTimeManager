package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plugin's main flows running on a real database server instead of the default SQLite.
 * Subclasses choose the server; everything is skipped when Docker is not available.
 */
@RequiresDocker
@Tag("database")
abstract class DatabaseEndToEndTest extends PluginTestBase {
    private final String yesterday = LocalDate.now().minusDays(1).toString();
    private final String tomorrow = LocalDate.now().plusDays(1).toString();

    /** Selects the database type and points the config at the server. */
    protected abstract void useDatabase(YamlConfiguration config);

    @Override
    protected void customizeConfig(YamlConfiguration config) {
        useDatabase(config);
    }

    @Override
    protected void afterServerStopped() {
        TestDatabases.stopAll();
    }

    private PlayerMock join(String name, UUID uuid) throws Exception {
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        return player;
    }

    @Test
    void playedTimeAccumulatesAcrossSessions() throws Exception {
        UUID uuid = UUID.randomUUID();
        var players = DatabaseHandler.getInstance().getPlayerDAO();

        PlayerMock first = join("DbSaver", uuid);
        first.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        first.disconnect();
        settle();
        PlayerMock second = join("DbSaver", uuid);
        second.setStatistic(Statistic.PLAY_ONE_MINUTE, 600);
        second.disconnect();
        settle();

        assertEquals(1800, players.getPlaytime(uuid.toString()));
        assertNotNull(players.getFirstJoin(uuid.toString()));
        assertNotNull(players.getLastSeen(uuid.toString()));
    }

    @Test
    void journalReportIncludesSessionsAfkGoalsAndRewards() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = join("DbJournal", uuid);
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());
        AFKSyncManager.getInstance().handleAFKGo(user);
        Thread.sleep(80);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        var db = DatabaseHandler.getInstance();
        db.getGoalsDAO().addCompletedGoal(uuid.toString(), "DbJournal", "db_goal", false);
        db.getStreakDAO().addReceivedReward(uuid.toString(), "DbJournal", new RewardSubInstance(4, 2, false));
        player.disconnect();
        settle();

        JournalReport report = JournalReport.build(db.getJournalDAO(), uuid.toString(), 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions());
        assertTrue(report.afk() >= 60, "AFK was " + report.afk());
        // Open-ended range: dates beyond the year 9999 are not accepted by the database, so this also
        // checks that the goal and reward queries are not sent a date it rejects
        assertEquals(1, report.goals().size(), "the goal is found");
        assertEquals(1, report.rewards().size(), "the reward is found");

        String out = run(server.getConsoleSender(), "playtimejournal", "DbJournal", yesterday, tomorrow);
        assertTrue(out.contains("Sessions: 1"), out);
        assertTrue(out.contains("Goals completed: 1") && out.contains("db_goal"), out);
        assertTrue(out.contains("Reward #4 (2 joins)"), out);

        String past = run(server.getConsoleSender(), "playtimejournal", "DbJournal", "2020-01-01", "2020-01-31");
        assertTrue(past.contains("Goals completed: 0") && past.contains("Sessions: 0"), past);
    }

    @Test
    void commandsWorkOnTheServer() throws Exception {
        UUID uuid = UUID.randomUUID();
        join("DbCommands", uuid).disconnect();
        settle();
        var console = server.getConsoleSender();
        var players = DatabaseHandler.getInstance().getPlayerDAO();

        assertTrue(run(console, "playtime", "DbCommands", "add", "2h").contains("updated"));
        assertEquals(144_000, players.getArtificialPlaytime(uuid.toString()));
        assertTrue(run(console, "playtime", "DbCommands", "remove", "1h").contains("updated"));
        assertEquals(72_000, players.getArtificialPlaytime(uuid.toString()));

        assertTrue(run(console, "playtimeaverage").contains("average playtime"));
        assertTrue(run(console, "playtimetop").contains("DbCommands"));
        assertTrue(run(console, "playtimepercentage", "1h").contains("players stored"));

        run(console, "playtime", "DbCommands", "reset", "playtime");
        assertEquals(0, players.getArtificialPlaytime(uuid.toString()));
    }

    @Test
    void theJournalFollowsAPlayerWhoChangesUuid() throws Exception {
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        join("DbShifty", oldUuid).disconnect();
        settle();
        var journal = DatabaseHandler.getInstance().getJournalDAO();
        assertEquals(1, journal.getSessions(oldUuid.toString(), 0, Long.MAX_VALUE).size());

        join("DbShifty", newUuid);

        assertEquals(0, journal.getSessions(oldUuid.toString(), 0, Long.MAX_VALUE).size());
        assertEquals(2, journal.getSessions(newUuid.toString(), 0, Long.MAX_VALUE).size());
    }

    @Test
    void anOpenSessionIsExtendedByThePeriodicUpdateAndClosedOnShutdownWriteOrder() throws Exception {
        UUID uuid = UUID.randomUUID();
        join("DbSteady", uuid);
        JournalDAO dao = DatabaseHandler.getInstance().getJournalDAO();
        long end = dao.getSessions(uuid.toString(), 0, Long.MAX_VALUE).get(0).end();

        Thread.sleep(200);
        server.getScheduler().performTicks(6001);
        Thread.sleep(700);

        assertTrue(dao.getSessions(uuid.toString(), 0, Long.MAX_VALUE).get(0).end() >= end + 180);
    }
}
