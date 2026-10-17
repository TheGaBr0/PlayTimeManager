package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every command that checks a permission itself must refuse a player who does not have it. */
@Tag("commands")
class PermissionsTest extends PluginTestBase {

    private void assertRefused(String permission, String command, String... args) throws Exception {
        PlayerMock player = server.addPlayer("Denied" + Math.abs((permission + command).hashCode() % 10_000));
        player.addAttachment(plugin, permission, false);
        settle();

        String out = run(player, command, args);
        assertTrue(out.contains("permission"), command + " should refuse without " + permission + ", said: " + out);
        assertFalse(out.contains("average playtime is") || out.contains("players stored")
                || out.contains("reloaded") || out.contains("backup created"), out);
    }

    @Test
    void average() throws Exception {
        assertRefused("playtime.average", "playtimeaverage");
    }

    @Test
    void percentage() throws Exception {
        assertRefused("playtime.percentage", "playtimepercentage", "1h");
    }

    @Test
    void top() throws Exception {
        assertRefused("playtime.top", "playtimetop");
    }

    @Test
    void reload() throws Exception {
        assertRefused("playtime.reload", "playtimereload");
    }

    @Test
    void backup() throws Exception {
        assertRefused("playtime.backup", "playtimebackup");
    }
}
