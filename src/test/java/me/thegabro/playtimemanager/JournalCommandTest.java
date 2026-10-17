package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimejournal end to end: parsing, permission and the report built from real journal rows. */
@Tag("journal")
class JournalCommandTest extends PluginTestBase {

    private final String yesterday = LocalDate.now().minusDays(1).toString();
    private final String tomorrow = LocalDate.now().plusDays(1).toString();

    private String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void reportsSessionAndAfkOfAPlayer() throws Exception {
        PlayerMock player = server.addPlayer("Judy");
        settle();
        OnlineUser user = OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
        AFKSyncManager.getInstance().handleAFKGo(user);
        Thread.sleep(80);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        player.disconnect();
        settle();

        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("playtimejournal", console, "Judy", yesterday, tomorrow).assertSucceeded();
        String out = joined(awaitMessages(console));

        assertTrue(out.contains("Journal of Judy"), out);
        assertTrue(out.contains("Sessions: 1"), out);
        assertTrue(out.contains("Online:"), out);
        assertTrue(out.contains("AFK:"), out);
        assertTrue(out.contains("Goals completed: 0"), out);
        assertTrue(out.contains("Join streak rewards received: 0"), out);
    }

    @Test
    void acceptsAnExplicitTime() throws Exception {
        PlayerMock player = server.addPlayer("Tim");
        settle();
        player.disconnect();
        settle();

        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("playtimejournal", console, "Tim", yesterday, "00:00", tomorrow, "23:59").assertSucceeded();
        String out = joined(awaitMessages(console));

        assertTrue(out.contains("Sessions: 1"), out);
    }

    @Test
    void aPeriodWithoutActivityIsEmpty() throws Exception {
        PlayerMock player = server.addPlayer("Old");
        settle();
        player.disconnect();
        settle();

        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("playtimejournal", console, "Old", "2020-01-01", "2020-01-31").assertSucceeded();
        String out = joined(awaitMessages(console));

        assertTrue(out.contains("Sessions: 0"), out);
    }

    @Test
    void rejectsMalformedAndInvertedPeriods() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();

        server.execute("playtimejournal", console, "Judy", "not-a-date", tomorrow);
        String usage = joined(awaitMessages(console));
        assertTrue(usage.contains("Usage"), usage);

        server.execute("playtimejournal", console, "Judy", tomorrow, yesterday);
        String inverted = joined(awaitMessages(console));
        assertTrue(inverted.contains("after its start"), inverted);
    }

    @Test
    void unknownPlayerIsReported() throws Exception {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("playtimejournal", console, "NobodyHere", yesterday, tomorrow);
        String out = joined(awaitMessages(console));

        assertTrue(out.contains("Player not found"), out);
    }

    @Test
    void requiresThePermission() throws Exception {
        PlayerMock nobody = server.addPlayer("Nobody");
        // MockBukkit grants permissions by default, so the denial has to be explicit
        nobody.addAttachment(plugin, "playtime.journal", false);
        settle();

        // Bukkit itself refuses (permission/permission-message in plugin.yml), so the command "succeeds"
        server.execute("playtimejournal", nobody, "Nobody", yesterday, tomorrow);
        net.kyori.adventure.text.Component refusal = nobody.nextComponentMessage();
        assertNotNull(refusal, "a refusal message is sent");
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(refusal);
        assertTrue(text.contains("permission"), text);
        assertNull(nobody.nextComponentMessage(), "no report is produced");
    }
}
