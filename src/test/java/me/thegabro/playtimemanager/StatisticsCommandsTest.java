package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimeaverage, /playtimepercentage and /playtimetop, on a database with known playtimes. */
@Tag("commands")
class StatisticsCommandsTest extends PluginTestBase {

    @BeforeAll
    void createPlayersWithKnownPlaytime() throws Exception {
        // settle() between join and quit: the plugin loads a joining player asynchronously
        PlayerMock ann = server.addPlayer("Ann");
        PlayerMock bob = server.addPlayer("Bob");
        settle();
        ann.disconnect();
        bob.disconnect();
        settle();
        ConsoleCommandSenderMock console = server.getConsoleSender();
        run(console, "playtime", "Ann", "add", "3h");
        run(console, "playtime", "Bob", "add", "1h");
    }

    @Test
    void averageIsReported() throws Exception {
        String out = run(server.getConsoleSender(), "playtimeaverage");
        assertTrue(out.contains("The average playtime is"), out);
    }

    @Test
    void percentageCountsThePlayersAboveTheThreshold() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        String atLeastOneHour = run(console, "playtimepercentage", "1h");
        assertTrue(atLeastOneHour.contains("are 2 and represent 100% of the 2 players"), atLeastOneHour);

        String atLeastTwoHours = run(console, "playtimepercentage", "2h");
        assertTrue(atLeastTwoHours.contains("are 1 and represent 50% of the 2 players"), atLeastTwoHours);
    }

    @Test
    void percentageRejectsMissingAndInvalidTimes() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        assertTrue(run(console, "playtimepercentage").contains("Too few arguments"));
        assertTrue(run(console, "playtimepercentage", "banana").contains("not specified correctly"));
    }

    @Test
    void topListsPlayersByPlaytime() throws Exception {
        String out = run(server.getConsoleSender(), "playtimetop");

        int ann = out.indexOf("Ann");
        int bob = out.indexOf("Bob");
        assertTrue(ann >= 0 && bob >= 0, out);
        assertTrue(ann < bob, "Ann (3h) comes before Bob (1h):\n" + out);
    }

    @Test
    void topRejectsAnInvalidOrMissingPage() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        String invalid = run(console, "playtimetop", "banana");
        assertFalse(invalid.contains("Ann"), invalid);
        assertFalse(invalid.isBlank(), "an error message is sent");

        String missingPage = run(console, "playtimetop", "p99");
        assertFalse(missingPage.contains("Ann"), missingPage);
        assertFalse(missingPage.isBlank(), "an error message is sent");
    }
}
