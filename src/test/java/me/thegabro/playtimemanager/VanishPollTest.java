package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.clip.placeholderapi.PlaceholderAPI;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Vanish detection by polling a PlaceholderAPI placeholder, with PlaceholderAPI emulated. */
@Tag("journal")
class VanishPollTest extends PluginTestBase {
    private static final String PLACEHOLDER = "%vanish_placeholder%"; // the default of config.yml

    @BeforeAll
    void enableVanishProtection() {
        plugin.getConfiguration().set("vanish-protection.enabled", true);
        // The poll schedule is only started at enable time, when the option was still off
        OnlineUsersManager.getInstance().initialize();
    }

    @Test
    void pollingThePlaceholderTogglesVanishAndJournalsIt() throws Exception {
        PlayerMock player = server.addPlayer("Vera");
        settle();
        String uuid = player.getUniqueId().toString();
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid);
        assertNotNull(user);
        assertFalse(OnlineUsersManager.getInstance().isCurrentlyVanished(user));

        PlaceholderAPI.setValue(player.getUniqueId(), PLACEHOLDER, "Yes");
        server.getScheduler().performTicks(120); // poll interval is 5 s = 100 ticks
        settle();
        assertTrue(OnlineUsersManager.getInstance().isCurrentlyVanished(user), "vanish detected by the poll");

        Thread.sleep(100);

        PlaceholderAPI.setValue(player.getUniqueId(), PLACEHOLDER, "No");
        server.getScheduler().performTicks(120);
        settle();
        assertFalse(OnlineUsersManager.getInstance().isCurrentlyVanished(user), "unvanish detected by the poll");

        player.disconnect();
        settle();

        JournalReport report = JournalReport.build(
                DatabaseHandler.getInstance().getJournalDAO(), uuid, 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions());
        assertTrue(report.vanish() >= 90, "vanished for ~100ms, was " + report.vanish());
        assertTrue(report.onlineVisible() >= 0 && report.onlineVisible() < report.online());
    }
}
