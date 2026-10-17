package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimejoinstreak: set a streak, look at another player's rewards, GUI and permissions. */
@Tag("joinstreak")
@Tag("commands")
class JoinStreakCommandTest extends PluginTestBase {

    private PlayerMock offlinePlayer(String name, UUID uuid) throws Exception {
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        player.disconnect();
        settle();
        return player;
    }

    private int streak(UUID uuid) {
        return DatabaseHandler.getInstance().getStreakDAO().getRelativeJoinStreak(uuid.toString());
    }

    @Test
    void setSetsTheStreakOfOnePlayer() throws Exception {
        UUID uuid = UUID.randomUUID();
        offlinePlayer("StreakSet", uuid);

        String out = run(server.getConsoleSender(), "playtimejoinstreak", "set", "StreakSet", "7");

        assertTrue(out.contains("Set join streak for player StreakSet") && out.contains("to 7"), out);
        assertEquals(7, streak(uuid));
    }

    @Test
    void setWithAWildcardSetsEveryone() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        offlinePlayer("WildStreakA", a);
        offlinePlayer("WildStreakB", b);
        run(server.getConsoleSender(), "playtimejoinstreak", "set", "WildStreakA", "9");

        String started = run(server.getConsoleSender(), "playtimejoinstreak", "set", "*", "2");
        String done = started + "\n" + String.join("\n", awaitMessages(server.getConsoleSender()));

        assertTrue(done.contains("have been set to 2"), done);
        assertEquals(2, streak(a));
        assertEquals(2, streak(b));
    }

    @Test
    void badInputChangesNothing() throws Exception {
        UUID uuid = UUID.randomUUID();
        offlinePlayer("StreakKeep", uuid);
        run(server.getConsoleSender(), "playtimejoinstreak", "set", "StreakKeep", "4");

        run(server.getConsoleSender(), "playtimejoinstreak", "set", "StreakKeep", "many");
        run(server.getConsoleSender(), "playtimejoinstreak", "set", "StreakKeep", "-3");
        String unknown = run(server.getConsoleSender(), "playtimejoinstreak", "set", "StreakNobody", "5");
        String tooFew = run(server.getConsoleSender(), "playtimejoinstreak", "set");

        assertEquals(4, streak(uuid));
        assertTrue(unknown.contains("never joined"), unknown);
        assertTrue(tooFew.contains("Too few arguments"), tooFew);
    }

    @Test
    void settingNeedsThePermission() throws Exception {
        UUID uuid = UUID.randomUUID();
        offlinePlayer("StreakGuarded", uuid);
        PlayerMock meddler = server.addPlayer("StreakMeddler");
        meddler.addAttachment(plugin, "playtime.others.modify", false);
        settle();

        String out = run(meddler, "playtimejoinstreak", "set", "StreakGuarded", "8");

        assertTrue(out.contains("permission"), out);
        assertEquals(0, streak(uuid));
    }

    @Test
    void seePlayerOpensTheirRewardsGui() throws Exception {
        offlinePlayer("StreakWatched", UUID.randomUUID());
        PlayerMock watcher = server.addPlayer("StreakWatcher");
        watcher.setOp(true); // seeplayer needs playtime.joinstreak.seeplayer, not granted to normal players
        settle();

        server.execute("playtimejoinstreak", watcher, "seeplayer", "StreakWatched");
        settle();

        assertNotNull(watcher.getOpenInventory().getTopInventory(), "the rewards of the other player are shown");
    }

    @Test
    void theGuiIsForPlayersOnly() throws Exception {
        assertTrue(run(server.getConsoleSender(), "playtimejoinstreak").contains("Only players"));
        assertTrue(run(server.getConsoleSender(), "playtimejoinstreak", "seeplayer", "Anyone").contains("player"));

        PlayerMock admin = server.addPlayer("StreakAdmin");
        admin.setOp(true); // playtime.joinstreak is not granted to normal players
        settle();
        server.execute("playtimejoinstreak", admin);
        settle();
        assertNotNull(admin.getOpenInventory().getTopInventory(), "the rewards GUI is open");
    }
}
