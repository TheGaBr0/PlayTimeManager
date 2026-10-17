package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** The %PTM_...% placeholders: own values, named players, rankings and what a vanished player shows. */
@Tag("placeholders")
class PlaceholdersFullTest extends PluginTestBase {

    private String ph(PlayerMock viewer, String params) {
        PlaceholderExpansion ptm = PlaceholderExpansion.REGISTERED.stream()
                .filter(e -> e.getIdentifier().equals("PTM")).findFirst().orElseThrow();
        return ptm.onRequest(viewer, params);
    }

    private double number(PlayerMock viewer, String params) {
        String value = ph(viewer, params);
        assertNotNull(value, params);
        return Double.parseDouble(value);
    }

    @Test
    void ownPlaytimeInEveryUnit() throws Exception {
        PlayerMock player = server.addPlayer("Unitary");
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 72_000); // exactly one hour

        assertEquals(3600, number(player, "playtime_s"));
        assertEquals(60, number(player, "playtime_m"));
        assertEquals(1, number(player, "playtime_h"));
        assertEquals(0, number(player, "afk_playtime_s"));
    }

    @Test
    void anotherPlayersPlaytimeCanBeAskedByName() throws Exception {
        PlayerMock viewer = server.addPlayer("Viewer");
        PlayerMock target = server.addPlayer("Observed2");
        settle();
        target.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);

        assertEquals(60, number(viewer, "playtime_s_Observed2"));
    }

    @Test
    void anUnknownNameIsLoadedThenReportedAsMissing() throws Exception {
        PlayerMock viewer = server.addPlayer("Asker");
        settle();

        // Whatever text the server is configured to show for a missing player, it is never a time
        String first = ph(viewer, "playtime_s_Nobody123");
        assertNotNull(first);
        assertThrows(NumberFormatException.class, () -> Double.parseDouble(first), "first answer: " + first);
        settle();
        String later = ph(viewer, "playtime_s_Nobody123");
        assertNotNull(later);
        assertThrows(NumberFormatException.class, () -> Double.parseDouble(later), "later answer: " + later);
        assertNotEquals("Loading...", later, "it is not still loading");
    }

    @Test
    void sessionTimeStartsAtTheJoin() throws Exception {
        PlayerMock player = server.addPlayer("Sessional");
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);

        assertEquals(60, number(player, "onlinefor_s"));
    }

    @Test
    void rankAndTopFollowThePlaytime() throws Exception {
        PlayerMock leader = server.addPlayer("Leader");
        PlayerMock follower = server.addPlayer("Follower");
        settle();
        leader.disconnect();
        follower.disconnect();
        settle();
        run(server.getConsoleSender(), "playtime", "Leader", "add", "50h");
        run(server.getConsoleSender(), "playtime", "Follower", "add", "40h");
        PlayerMock viewer = server.addPlayer("RankViewer");
        settle();

        assertEquals("Leader", ph(viewer, "nickname_top_1"));
        assertEquals("Follower", ph(viewer, "nickname_top_2"));
        assertEquals(50 * 3600, number(viewer, "playtime_top_s_1"));
        assertEquals(40 * 3600, number(viewer, "playtime_top_s_2"));
    }

    @Test
    void aVanishedPlayerShowsTheirTimeAsItWasWhenTheyVanished() throws Exception {
        PlayerMock ghost = server.addPlayer("Ghosty");
        PlayerMock viewer = server.addPlayer("Spectator");
        settle();
        ghost.setStatistic(Statistic.PLAY_ONE_MINUTE, 600); // 30 s
        var users = OnlineUsersManager.getInstance();
        var ghostUser = users.getOnlineUserByUUID(ghost.getUniqueId().toString());

        users.addVanishedPlayer(ghostUser, true);
        settle();
        ghost.setStatistic(Statistic.PLAY_ONE_MINUTE, 6000); // 300 s: invisible to everybody else

        assertEquals(30, number(viewer, "playtime_s_Ghosty"), "an observer asking by name sees the frozen value");
        assertEquals(30, number(ghost, "playtime_s"), "so does a self lookup through the placeholder");

        users.removeVanishedPlayer(ghostUser);
        settle();
        assertEquals(300, number(viewer, "playtime_s_Ghosty"), "back to the live value once visible again");
    }
}
