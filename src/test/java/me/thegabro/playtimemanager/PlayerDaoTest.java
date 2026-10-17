package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DAOs.PlayerDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The player table: reads and updates, the nickname change that touches the history tables, the reset. */
@Tag("database")
class PlayerDaoTest extends PluginTestBase {

    private PlayerDAO dao() {
        return DatabaseHandler.getInstance().getPlayerDAO();
    }

    private String newPlayer(String nickname, long playtime) {
        String uuid = UUID.randomUUID().toString();
        dao().addNewPlayer(uuid, nickname, playtime);
        return uuid;
    }

    private void sql(String... statements) throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String statement : statements) s.execute(statement);
        }
    }

    // ---- reading and updating -----------------------------------------------------------------

    @Test
    void aNewPlayerStartsWithItsTimeAndEverythingElseEmpty() {
        String uuid = newPlayer("DaoNew", 321);

        assertTrue(dao().playerExists(uuid));
        assertEquals("DaoNew", dao().getNickname(uuid));
        assertEquals(321, dao().getPlaytime(uuid));
        assertEquals(0, dao().getArtificialPlaytime(uuid));
        assertEquals(0, dao().getAFKPlaytime(uuid));
        assertEquals(uuid, dao().getUUIDFromNickname("DaoNew"));
        assertNotNull(dao().getFirstJoin(uuid), "the moment the player was added is their first join");
        assertTrue(Math.abs(dao().getFirstJoin(uuid).toEpochMilli() - System.currentTimeMillis()) < 10_000);
    }

    @Test
    void theTimesCanBeUpdatedOneByOne() {
        String uuid = newPlayer("DaoTimes", 0);

        dao().updatePlaytime(uuid, 100);
        dao().updateArtificialPlaytime(uuid, 200);
        dao().updateAFKPlaytime(uuid, 300);

        assertEquals(100, dao().getPlaytime(uuid));
        assertEquals(200, dao().getArtificialPlaytime(uuid));
        assertEquals(300, dao().getAFKPlaytime(uuid));
    }

    @Test
    void lastSeenAndFirstJoinKeepTheMillisecondAndCanBeCleared() {
        String uuid = newPlayer("DaoDates", 0);
        Instant moment = Instant.ofEpochMilli(1_760_000_000_123L);

        dao().updateLastSeen(uuid, moment);
        dao().updateFirstJoin(uuid, moment.minusSeconds(60));
        assertEquals(moment, dao().getLastSeen(uuid));
        assertEquals(moment.minusSeconds(60), dao().getFirstJoin(uuid));

        dao().updateLastSeen(uuid, null);
        dao().updateFirstJoin(uuid, null);
        assertNull(dao().getLastSeen(uuid));
        assertNull(dao().getFirstJoin(uuid));
    }

    @Test
    void unknownPlayersGiveNeutralAnswers() {
        String ghost = UUID.randomUUID().toString();

        assertFalse(dao().playerExists(ghost));
        assertEquals(0L, (long) dao().getPlaytime(ghost));
        assertEquals(0L, (long) dao().getArtificialPlaytime(ghost), "like the playtime, not null");
        assertEquals(0L, (long) dao().getAFKPlaytime(ghost));
        assertNull(dao().getNickname(ghost));
        assertNull(dao().getUUIDFromNickname("NobodyAtAllDao"));
        assertNull(dao().getLastSeen(ghost));
        assertNull(dao().getFirstJoin(ghost));
    }

    @Test
    void allNicknamesAreListed() {
        newPlayer("DaoListedA", 1);
        newPlayer("DaoListedB", 1);

        List<String> nicknames = dao().getAllNicknames();

        assertTrue(nicknames.containsAll(List.of("DaoListedA", "DaoListedB")));
    }

    @Test
    void playersSeenSinceAGivenMomentAreTheOnesSeenAtOrAfterIt() {
        String old = newPlayer("DaoSeenOld", 1);
        String recent = newPlayer("DaoSeenRecent", 1);
        String never = newPlayer("DaoSeenNever", 1);
        Instant now = Instant.now();
        dao().updateLastSeen(old, now.minusSeconds(3600));
        dao().updateLastSeen(recent, now.minusSeconds(5));

        List<String> seen = dao().getPlayersSeenSince(now.minusSeconds(60));

        assertTrue(seen.contains(recent));
        assertFalse(seen.contains(old));
        assertFalse(seen.contains(never), "no last seen means not seen");
        assertTrue(dao().getPlayersSeenSince(now.minusSeconds(7200)).contains(old), "a wider window includes the old one");
    }

    // ---- changing the nickname ------------------------------------------------------------------

    @Test
    void aNewNicknameIsWrittenEverywhereThePlayerAppears() throws Exception {
        String uuid = newPlayer("DaoOldName", 10);
        sql("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g','" + uuid + "','DaoOldName','2026-01-01 00:00:00',1)");
        DatabaseHandler.getInstance().getStreakDAO().addReceivedReward(uuid, "DaoOldName", new RewardSubInstance(1, 1, false));
        DatabaseHandler.getInstance().getStreakDAO().addRewardToBeClaimed(uuid, "DaoOldName", new RewardSubInstance(2, 2, false));
        String other = newPlayer("DaoOtherName", 10);
        sql("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g','" + other + "','DaoOtherName','2026-01-01 00:00:00',1)");

        dao().updateNickname(uuid, "DaoNewName");

        assertEquals("DaoNewName", dao().getNickname(uuid));
        assertEquals(uuid, dao().getUUIDFromNickname("DaoNewName"));
        assertNull(dao().getUUIDFromNickname("DaoOldName"));
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String table : List.of("completed_goals", "received_rewards", "rewards_to_be_claimed")) {
                var rs = s.executeQuery("SELECT DISTINCT nickname FROM " + table + " WHERE user_uuid = '" + uuid + "'");
                assertTrue(rs.next(), table + " has rows");
                assertEquals("DaoNewName", rs.getString(1), table);
                assertFalse(rs.next(), table + " has a single nickname");
            }
            var rs = s.executeQuery("SELECT nickname FROM completed_goals WHERE user_uuid = '" + other + "'");
            assertTrue(rs.next());
            assertEquals("DaoOtherName", rs.getString(1), "other players are untouched");
        }
    }

    @Test
    void theNicknameChangeIsAllOrNothing() throws Exception {
        String uuid = newPlayer("DaoAtomic", 10);
        sql("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g','" + uuid + "','DaoAtomic','2026-01-01 00:00:00',1)");
        // A history table that cannot be updated makes the whole change fail
        sql("DROP TABLE received_rewards");

        quietlyAndSilently(() -> dao().updateNickname(uuid, "DaoNotApplied"));

        assertEquals("DaoAtomic", dao().getNickname(uuid), "play_time was not left half-updated");
        sql("CREATE TABLE received_rewards (id INTEGER PRIMARY KEY AUTOINCREMENT, user_uuid VARCHAR(36) NOT NULL, " +
                "nickname VARCHAR(36) NOT NULL, main_instance_ID INT NOT NULL, required_joins INT NOT NULL, " +
                "received_at TEXT NOT NULL)");
    }

    // ---- reset --------------------------------------------------------------------------------

    @Test
    void resettingAPlayerEmptiesTheirDataAndOnlyTheirs() throws Exception {
        String uuid = newPlayer("DaoResetMe", 500);
        String other = newPlayer("DaoResetOther", 700);
        for (String u : new String[]{uuid, other}) {
            dao().updateArtificialPlaytime(u, 5);
            dao().updateAFKPlaytime(u, 6);
            dao().updateLastSeen(u, Instant.now());
            dao().updateFirstJoin(u, Instant.now());
            DatabaseHandler.getInstance().getStreakDAO().setRelativeJoinStreak(u, 3);
            DatabaseHandler.getInstance().getStreakDAO().setAbsoluteJoinStreak(u, 8);
            sql("INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g','" + u + "','x','2026-01-01 00:00:00',1)");
            DatabaseHandler.getInstance().getStreakDAO().addReceivedReward(u, "x", new RewardSubInstance(1, 1, false));
            DatabaseHandler.getInstance().getStreakDAO().addRewardToBeClaimed(u, "x", new RewardSubInstance(2, 2, false));
        }

        dao().resetUserInDatabase(uuid);

        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        assertEquals(0, dao().getPlaytime(uuid));
        assertEquals(0, dao().getArtificialPlaytime(uuid));
        assertEquals(0, dao().getAFKPlaytime(uuid));
        assertNull(dao().getLastSeen(uuid));
        assertNull(dao().getFirstJoin(uuid));
        assertEquals(0, streaks.getRelativeJoinStreak(uuid));
        assertEquals(0, streaks.getAbsoluteJoinStreak(uuid));
        assertTrue(DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid).isEmpty());
        assertTrue(streaks.getReceivedRewards(uuid).isEmpty());
        assertTrue(streaks.getRewardsToBeClaimed(uuid).isEmpty());
        assertEquals("DaoResetMe", dao().getNickname(uuid), "the player stays in the table, only emptied");

        assertEquals(700, dao().getPlaytime(other), "another player is untouched");
        assertEquals(8, streaks.getAbsoluteJoinStreak(other));
        assertEquals(1, DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(other).size());
        assertEquals(1, streaks.getReceivedRewards(other).size());
    }

    /**
     * The table-level reset leaves the playtime journal alone: it also has sessions open in memory, so
     * the reset command wipes it through the JournalManager (see JournalResetTest).
     */
    @Test
    void resettingAPlayerKeepsTheirJournal() throws Exception {
        String uuid = newPlayer("DaoJournalKept", 1);
        DatabaseHandler.getInstance().getJournalDAO().executeBatch(List.of(
                me.thegabro.playtimemanager.Database.DAOs.JournalDAO.insertSession(uuid, 1000),
                me.thegabro.playtimemanager.Database.DAOs.JournalDAO.updateSessionLogout(uuid, 1000, 2000)));

        dao().resetUserInDatabase(uuid);

        assertEquals(1, DatabaseHandler.getInstance().getJournalDAO().getSessions(uuid, 0, Long.MAX_VALUE).size());
    }

    // ---- a failing database ---------------------------------------------------------------------

    @Test
    void whenTheTableIsGoneNothingThrowsAndTheAnswersAreNeutral() throws Exception {
        String uuid = newPlayer("DaoBroken", 99);
        sql("ALTER TABLE play_time RENAME TO play_time_hidden");
        try {
            quietlyAndSilently(() -> {
                assertNull(dao().getNickname(uuid));
                assertEquals(0L, (long) dao().getPlaytime(uuid));
                assertEquals(0L, (long) dao().getArtificialPlaytime(uuid));
                assertEquals(0L, (long) dao().getAFKPlaytime(uuid));
                assertNull(dao().getUUIDFromNickname("DaoBroken"));
                assertTrue(dao().getAllNicknames().isEmpty());
                assertFalse(dao().playerExists(uuid));
                assertNull(dao().getLastSeen(uuid));
                assertNull(dao().getFirstJoin(uuid));
                assertTrue(dao().getPlayersSeenSince(Instant.EPOCH).isEmpty());
                // writes fail quietly too
                dao().updatePlaytime(uuid, 1);
                dao().updateArtificialPlaytime(uuid, 1);
                dao().updateAFKPlaytime(uuid, 1);
                dao().updateLastSeen(uuid, Instant.now());
                dao().updateFirstJoin(uuid, Instant.now());
                dao().updateNickname(uuid, "Whatever");
                dao().updateUUID(UUID.randomUUID().toString(), "DaoBroken");
                dao().addNewPlayer(UUID.randomUUID().toString(), "DaoBroken2", 1);
                dao().resetUserInDatabase(uuid);
            });
        } finally {
            sql("ALTER TABLE play_time_hidden RENAME TO play_time");
        }

        assertEquals(99, dao().getPlaytime(uuid), "and the data is intact once the table is back");
    }

    /** Runs something that logs errors (and prints a stack trace) on purpose, without showing them. */
    private void quietlyAndSilently(Runnable action) {
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        try {
            quietly(() -> {
                action.run();
                return null;
            });
        } finally {
            System.setErr(realErr);
        }
    }
}
