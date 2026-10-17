package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.clip.placeholderapi.PlaceholderAPI;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** AFK detection for plugins without a native hook: a PlaceholderAPI placeholder polled every second. */
@Tag("afk")
class AfkPlaceholderDetectionTest extends PluginTestBase {
    private static final String PLACEHOLDER = "%custom_afk%";

    @BeforeEach
    void enablePlaceholderDetection() {
        var config = plugin.getConfiguration();
        config.set("placeholder-afk-detection.enabled", true);
        config.set("placeholder-afk-detection.placeholder", PLACEHOLDER);
        config.set("placeholder-afk-detection.afk-value", "yes");
        // Same call /playtimereload makes to pick the settings up
        AFKManager.getInstance().handlePlaceholderAfkDetectionLoad(null);
    }

    private OnlineUser user(PlayerMock player) {
        return OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
    }

    @Test
    void thePlaceholderValueDecidesWhoIsAfk() throws Exception {
        PlayerMock player = server.addPlayer("Placeholdy");
        settle();
        assertFalse(user(player).isAFK());

        PlaceholderAPI.setValue(player.getUniqueId(), PLACEHOLDER, "yes");
        server.getScheduler().performTicks(25); // polled every 20 ticks
        assertTrue(user(player).isAFK(), "AFK as soon as the placeholder says so");

        PlaceholderAPI.setValue(player.getUniqueId(), PLACEHOLDER, "no");
        server.getScheduler().performTicks(25);
        assertFalse(user(player).isAFK(), "back when it stops saying so");
    }

    @Test
    void afkTimeIsJournaledAndCounted() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = new PlayerMock(server, "Dozer", uuid);
        server.addPlayer(player);
        settle();

        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 100);
        PlaceholderAPI.setValue(uuid, PLACEHOLDER, "yes");
        server.getScheduler().performTicks(25);
        Thread.sleep(80);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 600);
        PlaceholderAPI.setValue(uuid, PLACEHOLDER, "no");
        server.getScheduler().performTicks(25);
        player.disconnect();
        settle();

        var db = DatabaseHandler.getInstance();
        var intervals = db.getJournalDAO().getAfkIntervals(uuid.toString(), 0, Long.MAX_VALUE);
        assertEquals(1, intervals.size(), "one AFK interval in the journal");
        assertTrue(intervals.get(0).end() - intervals.get(0).start() >= 60);
        assertTrue(db.getPlayerDAO().getAFKPlaytime(uuid.toString()) > 0, "AFK ticks added to the player's total");
    }

    @Test
    void disablingTheDetectionStopsThePolling() throws Exception {
        PlayerMock player = server.addPlayer("Unwatched");
        settle();
        plugin.getConfiguration().set("placeholder-afk-detection.enabled", false);
        AFKManager.getInstance().handlePlaceholderAfkDetectionLoad(null);

        PlaceholderAPI.setValue(player.getUniqueId(), PLACEHOLDER, "yes");
        server.getScheduler().performTicks(60);

        assertFalse(user(player).isAFK(), "nobody is watching the placeholder anymore");
    }
}
