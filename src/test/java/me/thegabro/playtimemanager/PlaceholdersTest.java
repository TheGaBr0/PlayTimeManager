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
}
