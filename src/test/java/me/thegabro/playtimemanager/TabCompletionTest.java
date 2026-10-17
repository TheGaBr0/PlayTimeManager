package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("commands")
class TabCompletionTest extends PluginTestBase {

    private List<String> complete(CommandSender sender, String command, String... args) {
        return server.getCommandMap().getCommand(command).tabComplete(sender, command, args);
    }

    @Test
    void playtimeSuggestsPlayersThenSubcommandsThenResetTypes() throws Exception {
        server.addPlayer("Zelda");
        settle();
        var console = server.getConsoleSender();

        assertTrue(complete(console, "playtime", "Ze").contains("Zelda"));
        assertTrue(complete(console, "playtime", "").contains("*"), "the wildcard is offered with the permission");

        assertEquals(List.of("add", "remove", "reset"), complete(console, "playtime", "Zelda", ""));
        assertEquals(List.of("reset"), complete(console, "playtime", "*", ""), "the wildcard only supports reset");
        assertEquals(List.of("add"), complete(console, "playtime", "Zelda", "a"));

        List<String> resetTypes = complete(console, "playtime", "Zelda", "reset", "");
        assertTrue(resetTypes.containsAll(List.of("playtime", "server_playtime", "last_seen", "first_join",
                "joinstreak", "joinstreak_rewards", "goals", "everything")), resetTypes.toString());
        assertEquals(List.of("goals"), complete(console, "playtime", "Zelda", "reset", "go"));
    }

    @Test
    void playtimeSuggestionsRespectThePermissions() throws Exception {
        PlayerMock limited = server.addPlayer("Limited");
        limited.addAttachment(plugin, "playtime.others", false);
        settle();

        assertTrue(complete(limited, "playtime", "").isEmpty(), "no player names without playtime.others");

        PlayerMock noWildcard = server.addPlayer("NoWildcard");
        noWildcard.addAttachment(plugin, "playtime.others.modify.all", false);
        settle();
        assertFalse(complete(noWildcard, "playtime", "").contains("*"));
    }

    @Test
    void journalSuggestsPlayersAndADate() throws Exception {
        server.addPlayer("Jules");
        settle();
        var console = server.getConsoleSender();

        assertTrue(complete(console, "playtimejournal", "Ju").contains("Jules"));
        assertEquals(List.of(LocalDate.now().toString()), complete(console, "playtimejournal", "Jules", ""));
        assertTrue(complete(console, "playtimejournal", "Jules", "2026-01-01", "2026-01-02", "10:00", "x").isEmpty());
    }

    @Test
    void attributeSuggestsPlayersAttributeAndValues() throws Exception {
        server.addPlayer("Attrib");
        settle();
        var console = server.getConsoleSender();

        assertTrue(complete(console, "playtimeattribute", "At").contains("Attrib"));
        assertEquals(List.of("hidefromleaderboard"), complete(console, "playtimeattribute", "Attrib", "h"));
        assertEquals(List.of("true", "false"), complete(console, "playtimeattribute", "Attrib", "hidefromleaderboard", ""));
        assertEquals(List.of("true"), complete(console, "playtimeattribute", "Attrib", "hidefromleaderboard", "t"));
    }
}
