package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Disabling the plugin (server stop) closes every open session, AFK and vanish interval. */
@Tag("journal")
class JournalShutdownTest extends PluginTestBase {

    private long single(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getLong(1);
        }
    }

    @Test
    void disablingThePluginClosesWhatIsStillOpen() throws Exception {
        PlayerMock player = server.addPlayer("Closer");
        settle();
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
        AFKSyncManager.getInstance().handleAFKGo(user);
        OnlineUsersManager.getInstance().addVanishedPlayer(user, true);
        Thread.sleep(120);

        File database = new File(plugin.getDataFolder(), "play_time.db");
        plugin.onDisable(); // what happens when the server stops

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement s = c.createStatement()) {
            assertTrue(single(s, "SELECT logout - login FROM playtime_journal") >= 100,
                    "the session was closed at shutdown, not left at its first heartbeat");
            assertTrue(single(s, "SELECT afk_end - afk_start FROM afk_journal") >= 100);
            assertTrue(single(s, "SELECT vanish_end - vanish_start FROM vanish_journal") >= 100);
        }
    }
}
