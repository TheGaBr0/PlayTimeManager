package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /playtime * reset <type>: it asks for a confirmation and only the second run resets everybody. */
@Tag("commands")
class ResetAllCommandTest extends PluginTestBase {

    @Test
    void theSecondRunConfirmsAndResetsEveryone() throws Exception {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        for (var entry : java.util.Map.of("WildA", aId, "WildB", bId).entrySet()) {
            PlayerMock player = new PlayerMock(server, entry.getKey(), entry.getValue());
            server.addPlayer(player);
            settle();
            player.setStatistic(Statistic.PLAY_ONE_MINUTE, 2400);
            player.disconnect();
            settle();
        }
        ConsoleCommandSenderMock console = server.getConsoleSender();
        var players = DatabaseHandler.getInstance().getPlayerDAO();

        String warning = run(console, "playtime", "*", "reset", "playtime");
        assertTrue(warning.contains("WARNING") && warning.contains("ALL players"), warning);
        assertEquals(2400, players.getPlaytime(aId.toString()), "nothing is reset before the confirmation");
        assertEquals(2400, players.getPlaytime(bId.toString()));

        // The reset runs in the background: the start message comes first, the result later
        String started = run(console, "playtime", "*", "reset", "playtime");
        String done = started + "\n" + String.join("\n", awaitMessages(console));
        assertTrue(done.contains("has been reset"), done);
        assertEquals(0, players.getPlaytime(aId.toString()));
        assertEquals(0, players.getPlaytime(bId.toString()));
    }

    @Test
    void changingTheTypeAsksAgain() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        run(console, "playtime", "*", "reset", "goals");
        String out = run(console, "playtime", "*", "reset", "last_seen");

        assertTrue(out.contains("Reset type has changed"), out);
    }

    @Test
    void wildcardResetNeedsItsOwnPermission() throws Exception {
        PlayerMock admin = server.addPlayer("HalfAdmin");
        admin.addAttachment(plugin, "playtime.others.modify.all", false);
        settle();

        String out = run(admin, "playtime", "*", "reset", "playtime");
        assertTrue(out.contains("wildcards"), out);
    }
}
