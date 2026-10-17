package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.bukkit.Statistic;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimeattribute (hide from the leaderboard) and /playtimestats. */
@Tag("commands")
class AttributeAndStatsTest extends PluginTestBase {

    private void playedPlayer(String name, int ticks) throws Exception {
        PlayerMock player = server.addPlayer(name);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, ticks);
        player.disconnect();
        settle();
    }

    /** The GUI the player is looking at, or null (MockBukkit has no top inventory when none is open). */
    private Inventory openInventory(PlayerMock player) {
        InventoryView view = player.getOpenInventory();
        return view == null ? null : view.getTopInventory();
    }

    private boolean inLeaderboard(String name) throws Exception {
        return run(server.getConsoleSender(), "playtimetop").contains(name);
    }

    @Test
    void hidingAPlayerRemovesThemFromTheLeaderboard() throws Exception {
        playedPlayer("Hidey", 7200);
        ConsoleCommandSenderMock console = server.getConsoleSender();
        assertTrue(inLeaderboard("Hidey"), "visible by default");

        String hidden = run(console, "playtimeattribute", "Hidey", "hidefromleaderboard", "true");
        assertFalse(hidden.isBlank(), "the command answers");
        assertFalse(inLeaderboard("Hidey"), "hidden from the leaderboard");

        run(console, "playtimeattribute", "Hidey", "hidefromleaderboard", "false");
        assertTrue(inLeaderboard("Hidey"), "visible again");
    }

    @Test
    void withoutAValueTheAttributeIsToggled() throws Exception {
        playedPlayer("Toggly", 3600);
        ConsoleCommandSenderMock console = server.getConsoleSender();

        run(console, "playtimeattribute", "Toggly", "hidefromleaderboard");
        assertFalse(inLeaderboard("Toggly"), "first toggle hides");
        run(console, "playtimeattribute", "Toggly", "hidefromleaderboard");
        assertTrue(inLeaderboard("Toggly"), "second toggle shows again");
    }

    @Test
    void badAttributeCommandsAreRejected() throws Exception {
        playedPlayer("Picky", 100);
        ConsoleCommandSenderMock console = server.getConsoleSender();

        assertTrue(run(console, "playtimeattribute", "Picky").contains("Usage"));
        assertTrue(run(console, "playtimeattribute", "Picky", "nonsense").contains("Invalid attribute"));
        assertTrue(run(console, "playtimeattribute", "Picky", "hidefromleaderboard", "maybe").contains("Invalid value"));
        assertTrue(run(console, "playtimeattribute", "Nobody", "hidefromleaderboard", "true").contains("never joined"));
    }

    @Test
    void theAttributeNeedsThePermission() throws Exception {
        playedPlayer("Shielded", 5000);
        PlayerMock meddler = server.addPlayer("AttrMeddler");
        meddler.addAttachment(plugin, "playtime.others.attributes", false);
        settle();

        String out = run(meddler, "playtimeattribute", "Shielded", "hidefromleaderboard", "true");

        assertTrue(out.contains("permission"), out);
        assertTrue(inLeaderboard("Shielded"), "nothing changed");
    }

    @Test
    void statsShowTheNumbersToTheConsole() throws Exception {
        playedPlayer("Statty", 1200);
        run(server.getConsoleSender(), "playtime", "Statty", "add", "1h");

        String out = run(server.getConsoleSender(), "playtimestats", "Statty");

        assertTrue(out.contains("Player Stats"), out);
        assertTrue(out.contains("Player: Statty"), out);
        for (String section : new String[]{"Total Playtime", "Real Playtime", "Artificial Playtime",
                "AFK Playtime", "First Join", "Last Seen", "Join Streak"}) {
            assertTrue(out.contains(section), section + " missing in:\n" + out);
        }
    }

    @Test
    void statsOfAnUnknownPlayerAreRefused() throws Exception {
        String out = run(server.getConsoleSender(), "playtimestats", "NeverSeen");
        assertFalse(out.contains("Player Stats"), out);
        assertFalse(out.isBlank(), "the command says why");
    }

    @Test
    void aPlayerGetsTheStatsGui() throws Exception {
        PlayerMock player = server.addPlayer("Guier");
        settle();
        assertNull(openInventory(player), "setup: nothing open yet");

        server.execute("playtimestats", player);
        settle();

        Inventory gui = openInventory(player);
        assertNotNull(gui, "the stats GUI is open");
        assertTrue(gui.getSize() > 0);
    }

    @Test
    void statsOfOthersNeedThePermission() throws Exception {
        playedPlayer("Observed", 100);
        PlayerMock nosy = server.addPlayer("StatsNosy");
        nosy.addAttachment(plugin, "playtime.others.stats", false);
        settle();

        server.execute("playtimestats", nosy, "Observed");
        settle();

        assertNull(openInventory(nosy), "no GUI opened for a player without permission");
    }
}
