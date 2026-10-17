package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /playtime reset for the types and the ways not covered elsewhere: server playtime, join streak
 * rewards, every type for all players, the confirmation, and its expiry.
 */
@Tag("commands")
class ResetAllTypesTest extends PluginTestBase {

    private ConsoleCommandSenderMock console() {
        return server.getConsoleSender();
    }

    /** A player who left with a bit of everything: time, goals, a streak and rewards. */
    private UUID playerWithEverything(String name) throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        player.disconnect();
        settle();
        var db = DatabaseHandler.getInstance();
        db.getGoalsDAO().addCompletedGoal(uuid.toString(), name, "goal_" + name, false);
        db.getStreakDAO().setRelativeJoinStreak(uuid.toString(), 4);
        db.getStreakDAO().setAbsoluteJoinStreak(uuid.toString(), 9);
        db.getStreakDAO().addReceivedReward(uuid.toString(), name, new RewardSubInstance(1, 2, false));
        db.getStreakDAO().addRewardToBeClaimed(uuid.toString(), name, new RewardSubInstance(3, 4, false));
        return uuid;
    }

    private void clearPlayers() throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String table : new String[]{"completed_goals", "received_rewards", "rewards_to_be_claimed", "play_time"}) {
                s.execute("DELETE FROM " + table);
            }
        }
    }

    /** Runs the command for everybody (the start message first, the result later). */
    private String resetEveryone(String type) throws Exception {
        String asked = run(console(), "playtime", "*", "reset", type);
        assertTrue(asked.contains("WARNING"), "the first run only asks for confirmation: " + asked);
        String started = run(console(), "playtime", "*", "reset", type);
        return started + "\n" + String.join("\n", awaitMessages(console()));
    }

    // ---- server playtime -----------------------------------------------------------------------

    @Test
    void serverPlaytimeOfAnOnlinePlayerGoesBackToZero() throws Exception {
        PlayerMock player = server.addPlayer("ServerTimeOnline");
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 6000);

        String out = run(console(), "playtime", "ServerTimeOnline", "reset", "server_playtime");

        assertTrue(out.contains("Reset server playtime for player ServerTimeOnline"), out);
        assertEquals(0, player.getStatistic(Statistic.PLAY_ONE_MINUTE), "the statistic of the server is zero");
        var user = me.thegabro.playtimemanager.Users.OnlineUsersManager.getInstance()
                .getOnlineUserByUUID(player.getUniqueId().toString());
        assertEquals(0, user.getSessionPlaytime(), "and the plugin counts from there, without negative time");
    }

    @Test
    void serverPlaytimeOfAnOfflinePlayerIsHandled() throws Exception {
        UUID uuid = playerWithEverything("ServerTimeOffline");

        String out = run(console(), "playtime", "ServerTimeOffline", "reset", "server_playtime");

        assertTrue(out.contains("Reset server playtime for player ServerTimeOffline"), out);
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()),
                "the plugin's own playtime is not touched by this type");
    }

    @Test
    void serverPlaytimeOfEveryoneOnlineGoesBackToZero() throws Exception {
        PlayerMock a = server.addPlayer("ServerAllA");
        PlayerMock b = server.addPlayer("ServerAllB");
        settle();
        a.setStatistic(Statistic.PLAY_ONE_MINUTE, 500);
        b.setStatistic(Statistic.PLAY_ONE_MINUTE, 900);

        String out = resetEveryone("server_playtime");

        assertTrue(out.contains("server playtime has been reset"), out);
        assertEquals(0, a.getStatistic(Statistic.PLAY_ONE_MINUTE));
        assertEquals(0, b.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    // ---- join streak rewards -------------------------------------------------------------------

    @Test
    void joinStreakRewardsOfOnePlayerAreCleared() throws Exception {
        UUID uuid = playerWithEverything("RewardsReset");
        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        assertEquals(1, streaks.getReceivedRewards(uuid.toString()).size(), "setup");

        String out = run(console(), "playtime", "RewardsReset", "reset", "joinstreak_rewards");

        assertTrue(out.contains("Reset join streak rewards for player RewardsReset"), out);
        assertTrue(streaks.getReceivedRewards(uuid.toString()).isEmpty());
        assertEquals(9, streaks.getAbsoluteJoinStreak(uuid.toString()), "the streak itself is not touched");
    }

    // ---- every type, for everybody -------------------------------------------------------------

    @Test
    void goalsOfEveryoneAreCleared() throws Exception {
        UUID a = playerWithEverything("AllGoalsA");
        UUID b = playerWithEverything("AllGoalsB");

        String out = resetEveryone("goals");

        assertTrue(out.contains("goals has been reset") && out.contains("completed goals"), out);
        var goals = DatabaseHandler.getInstance().getGoalsDAO();
        assertTrue(goals.getCompletedGoals(a.toString()).isEmpty());
        assertTrue(goals.getCompletedGoals(b.toString()).isEmpty());
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(a.toString()), "time untouched");
    }

    @Test
    void joinStreaksOfEveryoneAreCleared() throws Exception {
        UUID a = playerWithEverything("AllStreakA");
        UUID b = playerWithEverything("AllStreakB");

        String out = resetEveryone("joinstreak");

        assertTrue(out.contains("join streaks has been reset"), out);
        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        for (UUID uuid : new UUID[]{a, b}) {
            assertEquals(0, streaks.getRelativeJoinStreak(uuid.toString()));
            assertEquals(0, streaks.getAbsoluteJoinStreak(uuid.toString()));
        }
    }

    @Test
    void joinStreakRewardsOfEveryoneAreCleared() throws Exception {
        UUID a = playerWithEverything("AllRewardsA");

        String out = resetEveryone("joinstreak_rewards");

        assertTrue(out.contains("join streak rewards has been reset"), out);
        assertTrue(DatabaseHandler.getInstance().getStreakDAO().getReceivedRewards(a.toString()).isEmpty());
    }

    @Test
    void lastSeenAndFirstJoinOfEveryoneAreCleared() throws Exception {
        UUID a = playerWithEverything("AllDatesA");
        var players = DatabaseHandler.getInstance().getPlayerDAO();
        assertNotNull(players.getLastSeen(a.toString()), "setup");

        resetEveryone("last_seen");
        assertNull(players.getLastSeen(a.toString()));
        assertNotNull(players.getFirstJoin(a.toString()), "first join is a different type");

        resetEveryone("first_join");
        assertNull(players.getFirstJoin(a.toString()));
    }

    @Test
    void everythingOfEveryoneIsCleared() throws Exception {
        UUID a = playerWithEverything("AllEverythingA");
        UUID b = playerWithEverything("AllEverythingB");

        String out = resetEveryone("everything");

        assertTrue(out.contains("data has been reset"), out);
        var db = DatabaseHandler.getInstance();
        for (UUID uuid : new UUID[]{a, b}) {
            assertEquals(0, db.getPlayerDAO().getPlaytime(uuid.toString()));
            assertTrue(db.getGoalsDAO().getCompletedGoals(uuid.toString()).isEmpty());
            assertTrue(db.getStreakDAO().getReceivedRewards(uuid.toString()).isEmpty());
            assertTrue(db.getStreakDAO().getRewardsToBeClaimed(uuid.toString()).isEmpty());
            assertEquals(0, db.getStreakDAO().getAbsoluteJoinStreak(uuid.toString()));
        }
    }

    @Test
    void withNobodyInTheDatabaseTheCommandSaysSo() throws Exception {
        clearPlayers();

        String out = resetEveryone("goals");

        assertTrue(out.contains("No players found"), out);
    }

    // ---- the confirmation ----------------------------------------------------------------------

    @Test
    void anUnknownTypeIsRefusedAfterTheConfirmation() throws Exception {
        UUID uuid = playerWithEverything("UnknownTypeAll");

        String asked = run(console(), "playtime", "*", "reset", "banana");
        assertTrue(asked.contains("unknown data"), asked);
        String confirmed = run(console(), "playtime", "*", "reset", "banana");

        assertTrue(confirmed.contains("Unknown reset type"), confirmed);
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()), "nothing was reset");
    }

    @Test
    void aConfirmationLeftForTooLongExpires() throws Exception {
        UUID uuid = playerWithEverything("ExpiringAll");
        run(console(), "playtime", "*", "reset", "playtime");
        ageTheConsolePendingConfirmation();

        String out = run(console(), "playtime", "*", "reset", "playtime");

        assertTrue(out.contains("expired"), out);
        assertTrue(out.contains("WARNING"), "and it asks again");
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()), "nothing was reset");
    }

    @Test
    void eachSenderHasTheirOwnConfirmation() throws Exception {
        UUID uuid = playerWithEverything("TwoSendersAll");
        PlayerMock admin = server.addPlayer("TwoSendersAdmin");
        admin.setOp(true);
        settle();

        run(console(), "playtime", "*", "reset", "playtime"); // the console is asked...
        String adminFirst = run(admin, "playtime", "*", "reset", "playtime");

        assertTrue(adminFirst.contains("WARNING"), "...but that does not confirm the admin's: " + adminFirst);
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()));
    }

    /** Moves the start of the console's pending confirmation back by more than the 60 seconds it lasts. */
    @SuppressWarnings("unchecked")
    private void ageTheConsolePendingConfirmation() throws Exception {
        Class<?> reset = Class.forName("me.thegabro.playtimemanager.Commands.PlayTimeReset");
        Field pendingField = reset.getDeclaredField("pendingResets");
        pendingField.setAccessible(true);
        Map<UUID, Object> pending = (Map<UUID, Object>) pendingField.get(null);
        UUID consoleUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Object entry = pending.get(consoleUuid);
        assertNotNull(entry, "setup: the console has a pending confirmation");
        Field timestamp = entry.getClass().getDeclaredField("timestamp");
        timestamp.setAccessible(true);
        timestamp.setLong(entry, System.currentTimeMillis() - 61_000);
    }
}
