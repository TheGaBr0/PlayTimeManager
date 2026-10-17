package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.purpurmc.purpur.event.PlayerAFKEvent;

import static org.junit.jupiter.api.Assertions.*;

/** AFK detection through a plugin: Purpur's event is emulated, the other plugins are checked as missing. */
@Tag("afk")
class AfkPluginDetectionTest extends PluginTestBase {

    private void configure(String afkPlugin) {
        plugin.getConfiguration().set("afk-detection-plugin", afkPlugin);
        AFKManager.getInstance().initialize(plugin);
    }

    private OnlineUser user(PlayerMock player) {
        return OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
    }

    private PlayerAFKEvent afkEvent(PlayerMock player, boolean goingAfk) {
        return new PlayerAFKEvent(player, goingAfk, false, "", false);
    }

    @Test
    void purpurAfkEventsAreFollowed() throws Exception {
        configure("purpur");
        assertTrue(AFKManager.getInstance().isAfkDetectionConfigured());
        assertEquals("purpur", AFKManager.getInstance().getConfiguredAFKPlugin());
        PlayerMock player = server.addPlayer("Purply");
        settle();

        server.getPluginManager().callEvent(afkEvent(player, true));
        assertTrue(user(player).isAFK());
        settle();
        assertEquals(1, DatabaseHandler.getInstance().getJournalDAO()
                .getAfkIntervals(player.getUniqueId().toString(), 0, Long.MAX_VALUE).size());

        server.getPluginManager().callEvent(afkEvent(player, false));
        assertFalse(user(player).isAFK());
    }

    @Test
    void aCancelledPurpurEventIsIgnored() throws Exception {
        configure("purpur");
        PlayerMock player = server.addPlayer("Vetoed");
        settle();

        PlayerAFKEvent event = afkEvent(player, true);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);

        assertFalse(user(player).isAFK());
    }

    @Test
    void aPluginThatIsNotInstalledDisablesTheDetection() {
        for (String missing : new String[]{"essentials", "antiafkplus", "afkplus", "jetsantiafkpro"}) {
            configure(missing);
            assertFalse(AFKManager.getInstance().isAfkDetectionConfigured(), missing + " is not installed");
        }
    }

    @Test
    void noneMeansNoDetection() {
        configure("none");
        assertFalse(AFKManager.getInstance().isAfkDetectionConfigured());
    }
}
