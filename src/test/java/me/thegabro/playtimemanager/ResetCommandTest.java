package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /playtime <player> reset [type] for a single player. */
@Tag("commands")
class ResetCommandTest extends PluginTestBase {

    /** A player that joined, played 1 minute, got 1 hour added, a goal and a join streak, then left. */
    private UUID playerWithData(String name) throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        player.disconnect();
        settle();

        run(server.getConsoleSender(), "playtime", name, "add", "1h");
        var db = DatabaseHandler.getInstance();
        db.getGoalsDAO().addCompletedGoal(uuid.toString(), name, "a_goal", false);
        db.getStreakDAO().setRelativeJoinStreak(uuid.toString(), 4);
        db.getStreakDAO().setAbsoluteJoinStreak(uuid.toString(), 9);

        assertEquals(1200, db.getPlayerDAO().getPlaytime(uuid.toString()), "setup: played time");
        assertEquals(72_000, db.getPlayerDAO().getArtificialPlaytime(uuid.toString()), "setup: added time");
        return uuid;
    }

    private ConsoleCommandSenderMock console() {
        return server.getConsoleSender();
    }

    @Test
    void resetPlaytimeClearsPlayedAndAddedTime() throws Exception {
        UUID uuid = playerWithData("PtReset");

        String out = run(console(), "playtime", "PtReset", "reset", "playtime");
        assertTrue(out.contains("Reset playtime for player PtReset"), out);

        var players = DatabaseHandler.getInstance().getPlayerDAO();
        assertEquals(0, players.getPlaytime(uuid.toString()));
        assertEquals(0, players.getArtificialPlaytime(uuid.toString()));
        assertEquals(1, DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid.toString()).size(),
                "only the playtime was reset");
    }

    @Test
    void resetLastSeenAndFirstJoin() throws Exception {
        UUID uuid = playerWithData("DatesReset");
        var players = DatabaseHandler.getInstance().getPlayerDAO();
        assertNotNull(players.getLastSeen(uuid.toString()));
        assertNotNull(players.getFirstJoin(uuid.toString()));

        assertTrue(run(console(), "playtime", "DatesReset", "reset", "last_seen").contains("Reset last seen"));
        assertNull(players.getLastSeen(uuid.toString()));
        assertNotNull(players.getFirstJoin(uuid.toString()), "first join untouched");

        assertTrue(run(console(), "playtime", "DatesReset", "reset", "first_join").contains("Reset first join"));
        assertNull(players.getFirstJoin(uuid.toString()));
    }

    @Test
    void resetJoinstreakClearsBothCounters() throws Exception {
        UUID uuid = playerWithData("StreakReset");

        String out = run(console(), "playtime", "StreakReset", "reset", "joinstreak");
        assertTrue(out.contains("Reset join streak for player StreakReset"), out);

        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        assertEquals(0, streaks.getRelativeJoinStreak(uuid.toString()));
        assertEquals(0, streaks.getAbsoluteJoinStreak(uuid.toString()));
    }

    @Test
    void resetGoalsClearsCompletedGoals() throws Exception {
        UUID uuid = playerWithData("GoalsReset");

        String out = run(console(), "playtime", "GoalsReset", "reset", "goals");
        assertTrue(out.contains("Reset goals for player GoalsReset"), out);

        assertTrue(DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(uuid.toString()).isEmpty());
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()),
                "only the goals were reset");
    }

    @Test
    void resetEverythingIsTheDefault() throws Exception {
        UUID uuid = playerWithData("AllReset");

        String out = run(console(), "playtime", "AllReset", "reset");
        assertTrue(out.contains("Reset everything for player AllReset"), out);

        var db = DatabaseHandler.getInstance();
        assertEquals(0, db.getPlayerDAO().getPlaytime(uuid.toString()));
        assertEquals(0, db.getPlayerDAO().getArtificialPlaytime(uuid.toString()));
        assertTrue(db.getGoalsDAO().getCompletedGoals(uuid.toString()).isEmpty());
        assertEquals(0, db.getStreakDAO().getRelativeJoinStreak(uuid.toString()));
        assertEquals(0, db.getStreakDAO().getAbsoluteJoinStreak(uuid.toString()));
    }

    @Test
    void unknownTypeAndUnknownPlayerChangeNothing() throws Exception {
        UUID uuid = playerWithData("Careful2");

        String unknownType = run(console(), "playtime", "Careful2", "reset", "banana");
        assertTrue(unknownType.contains("Unknown reset type"), unknownType);
        String unknownPlayer = run(console(), "playtime", "NobodyAtAll", "reset", "playtime");
        assertTrue(unknownPlayer.contains("never joined"), unknownPlayer);

        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()));
    }

    @Test
    void resettingNeedsThePermission() throws Exception {
        UUID uuid = playerWithData("Protected");
        PlayerMock meddler = server.addPlayer("ResetMeddler");
        meddler.addAttachment(plugin, "playtime.others.modify", false);
        settle();

        String out = run(meddler, "playtime", "Protected", "reset", "playtime");
        assertTrue(out.contains("permission"), out);
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()));
    }
}
