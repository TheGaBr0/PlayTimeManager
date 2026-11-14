package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** The PTM expansion, registered and queried the way PlaceholderAPI would (emulated). */
@Tag("placeholders")
class PlaceholdersTest extends PluginTestBase {

    private PlaceholderExpansion ptm() {
        return PlaceholderExpansion.REGISTERED.stream()
                .filter(e -> e.getIdentifier().equals("PTM"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the PTM expansion was not registered"));
    }

    @Test
    void expansionIsRegistered() {
        assertNotNull(ptm());
    }

    @Test
    void playtimePlaceholdersAnswerForAnOnlinePlayer() throws Exception {
        PlayerMock player = server.addPlayer("Pat");
        settle();

        assertNotNull(ptm().onRequest(player, "playtime"));
        assertNotNull(ptm().onRequest(player, "afk_playtime"));
        assertNull(ptm().onRequest(player, "this_is_not_a_placeholder"));
    }

    @Test
    void rollingWindowPlaceholdersAreRecognisedAndOthersUntouched() throws Exception {
        PlayerMock player = server.addPlayer("Pat");
        settle();

        // First read may be "Loading..." while the cache fills, but it must never be null (unhandled)
        for (String p : new String[]{"playtime_last24h", "playtime_last7d_s", "afk_playtime_last1h_m",
                "playtime_last7d_h_Pat", "playtime_top_last7d_1", "playtime_top_last7d_h_1",
                "nickname_top_last30d_1"}) {
            assertNotNull(ptm().onRequest(player, p), p);
        }
        // Not a window: still handled by the existing placeholders
        assertNotNull(ptm().onRequest(player, "playtime_h"));
        assertNotNull(ptm().onRequest(player, "playtime_top_1"));

        // The cache loads in the background: the value becomes a number shortly after
        String value = null;
        for (int i = 0; i < 50 && (value == null || !value.matches("\\d+")); i++) {
            Thread.sleep(100);
            value = ptm().onRequest(player, "playtime_last1h_s");
        }
        assertTrue(value.matches("\\d+"), "expected seconds, got " + value);
    }
}
