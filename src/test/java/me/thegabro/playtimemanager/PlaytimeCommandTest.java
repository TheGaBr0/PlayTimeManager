package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** /playtime: own and other players' time, and the add/remove subcommands. */
@Tag("commands")
class PlaytimeCommandTest extends PluginTestBase {

    private long artificialTicks(PlayerMock player) {
        return DatabaseHandler.getInstance().getPlayerDAO().getArtificialPlaytime(player.getUniqueId().toString());
    }

    @Test
    void aPlayerSeesTheirOwnPlaytime() throws Exception {
        PlayerMock player = server.addPlayer("Selfie");
        settle();

        String out = run(player, "playtime");
        assertTrue(out.contains("Your playtime is"), out);
    }

    @Test
    void theConsoleCannotCheckItsOwnPlaytime() throws Exception {
        String out = run(server.getConsoleSender(), "playtime");
        assertTrue(out.contains("must be a player"), out);
    }

    @Test
    void anotherPlayersPlaytimeIsShown() throws Exception {
        server.addPlayer("Target");
        settle();

        String out = run(server.getConsoleSender(), "playtime", "Target");
        assertTrue(out.contains("The playtime of Target is"), out);
    }

    @Test
    void anUnknownPlayerIsReported() throws Exception {
        String out = run(server.getConsoleSender(), "playtime", "NeverJoined");
        assertTrue(out.contains("NeverJoined") && out.contains("never joined"), out);
    }

    @Test
    void checkingOthersNeedsThePermission() throws Exception {
        PlayerMock nosy = server.addPlayer("Nosy");
        nosy.addAttachment(plugin, "playtime.others", false);
        server.addPlayer("Victim");
        settle();

        String out = run(nosy, "playtime", "Victim");
        assertTrue(out.contains("permission"), out);
        assertFalse(out.contains("The playtime of"), out);
    }

    @Test
    void addAndRemoveChangeTheArtificialPlaytime() throws Exception {
        PlayerMock player = server.addPlayer("Editable");
        settle();
        ConsoleCommandSenderMock console = server.getConsoleSender();
        assertEquals(0, artificialTicks(player));

        String added = run(console, "playtime", "Editable", "add", "1h");
        assertTrue(added.contains("Editable") && added.contains("updated"), added);
        assertEquals(72_000, artificialTicks(player), "1 hour = 72000 ticks");

        String removed = run(console, "playtime", "Editable", "remove", "30m");
        assertTrue(removed.contains("Editable") && removed.contains("updated"), removed);
        assertEquals(36_000, artificialTicks(player), "30 minutes removed");
    }

    @Test
    void modifyingNeedsThePermission() throws Exception {
        PlayerMock meddler = server.addPlayer("Meddler");
        meddler.addAttachment(plugin, "playtime.others.modify", false);
        PlayerMock victim = server.addPlayer("Untouched");
        settle();

        String out = run(meddler, "playtime", "Untouched", "add", "1h");
        assertTrue(out.contains("permission"), out);
        assertEquals(0, artificialTicks(victim));
    }

    @Test
    void badArgumentsAreRejectedWithoutChangingAnything() throws Exception {
        PlayerMock player = server.addPlayer("Careful");
        settle();
        ConsoleCommandSenderMock console = server.getConsoleSender();

        String tooFew = run(console, "playtime", "Careful", "add");
        assertTrue(tooFew.contains("Too few arguments"), tooFew);
        String badTime = run(console, "playtime", "Careful", "add", "banana");
        assertTrue(badTime.contains("Invalid time format"), badTime);
        String unknown = run(console, "playtime", "Careful", "dance");
        assertTrue(unknown.contains("Unknown subcommand"), unknown);
        String ghost = run(console, "playtime", "Ghost", "add", "1h");
        assertTrue(ghost.contains("never joined"), ghost);
        assertEquals(0, artificialTicks(player));
    }
}
