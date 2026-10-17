package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Interval;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.ExternalPluginSupport.AFKSyncManager;
import me.thegabro.playtimemanager.Journal.JournalManager;
import me.thegabro.playtimemanager.Journal.JournalReport;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /playtime <player|*> reset journal: wipes the history of sessions, and keeps recording what comes next. */
@Tag("journal")
@Tag("commands")
class JournalResetTest extends PluginTestBase {

    private JournalDAO journal() {
        return DatabaseHandler.getInstance().getJournalDAO();
    }

    private ConsoleCommandSenderMock console() {
        return server.getConsoleSender();
    }

    private List<Interval> sessions(UUID uuid) {
        return journal().getSessions(uuid.toString(), 0, Long.MAX_VALUE);
    }

    /** A player who played, was AFK for a moment, and left: one finished session with one AFK interval. */
    private UUID playerWithHistory(String name) throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = new PlayerMock(server, name, uuid);
        server.addPlayer(player);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200);
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());
        AFKSyncManager.getInstance().handleAFKGo(user);
        Thread.sleep(40);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        player.disconnect();
        settle();
        assertEquals(1, sessions(uuid).size(), "setup: one session");
        assertEquals(1, journal().getAfkIntervals(uuid.toString(), 0, Long.MAX_VALUE).size(), "setup: one AFK interval");
        return uuid;
    }

    @Test
    void theJournalOfOnePlayerIsWipedAndNothingElse() throws Exception {
        UUID target = playerWithHistory("JournalTarget");
        UUID other = playerWithHistory("JournalBystander");

        String out = run(console(), "playtime", "JournalTarget", "reset", "journal");

        assertTrue(out.contains("Reset journal for player JournalTarget") && out.contains("Removed 1 sessions"), out);
        assertTrue(sessions(target).isEmpty());
        assertTrue(journal().getAfkIntervals(target.toString(), 0, Long.MAX_VALUE).isEmpty(), "the AFK intervals go too");
        assertEquals(1, sessions(other).size(), "another player's journal is untouched");
        assertEquals(1200, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(target.toString()),
                "so is the rest of the player's own data");
    }

    @Test
    void resettingEverythingWipesTheJournalToo() throws Exception {
        UUID uuid = playerWithHistory("JournalWipedByEverything");
        UUID other = playerWithHistory("JournalBystander3");

        String out = run(console(), "playtime", "JournalWipedByEverything", "reset", "everything");

        assertTrue(out.contains("Reset everything for player JournalWipedByEverything"), out);
        assertTrue(sessions(uuid).isEmpty(), "the journal is part of everything");
        assertTrue(journal().getAfkIntervals(uuid.toString(), 0, Long.MAX_VALUE).isEmpty());
        assertEquals(0, DatabaseHandler.getInstance().getPlayerDAO().getPlaytime(uuid.toString()));
        assertEquals(1, sessions(other).size(), "another player's journal is untouched");
    }

    @Test
    void resettingEverythingForEveryoneWipesAllJournalsAndKeepsOnlinePlayersRecorded() throws Exception {
        UUID a = playerWithHistory("JournalEverythingA");
        PlayerMock online = server.addPlayer("JournalEverythingOnline");
        settle();

        run(console(), "playtime", "*", "reset", "everything");
        run(console(), "playtime", "*", "reset", "everything");
        awaitMessages(console());
        settle();

        assertTrue(sessions(a).isEmpty());
        assertEquals(1, sessions(online.getUniqueId()).size(), "the player online has a new session going");
    }

    @Test
    void resettingOnlyTheOtherDataLeavesTheJournal() throws Exception {
        UUID uuid = playerWithHistory("JournalKeptByPlaytime");

        run(console(), "playtime", "JournalKeptByPlaytime", "reset", "playtime");
        run(console(), "playtime", "JournalKeptByPlaytime", "reset", "goals");

        assertEquals(1, sessions(uuid).size(), "only 'journal' and 'everything' remove it");
    }

    @Test
    void aPlayerWhoIsOnlineKeepsBeingRecorded() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = new PlayerMock(server, "JournalOnline", uuid);
        server.addPlayer(player);
        settle();
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(uuid.toString());
        AFKSyncManager.getInstance().handleAFKGo(user); // AFK while the reset happens
        OnlineUsersManager.getInstance().addVanishedPlayer(user, true); // and vanished
        Thread.sleep(40);
        settle();
        long oldLogin = sessions(uuid).get(0).start();

        String out = run(console(), "playtime", "JournalOnline", "reset", "journal");
        assertTrue(out.contains("Reset journal for player JournalOnline"), out);

        List<Interval> afterReset = sessions(uuid);
        assertEquals(1, afterReset.size(), "the session in progress starts again, it does not vanish");
        assertTrue(afterReset.get(0).start() > oldLogin, "from the moment of the reset");
        assertEquals(1, journal().getAfkIntervals(uuid.toString(), 0, Long.MAX_VALUE).size(), "still AFK");
        assertEquals(1, journal().getVanishIntervals(uuid.toString(), 0, Long.MAX_VALUE).size(), "still vanished");

        // What follows is recorded on the new session: back from AFK, then leaving
        Thread.sleep(60);
        AFKSyncManager.getInstance().handleAFKReturn(user);
        player.disconnect();
        settle();

        JournalReport report = JournalReport.build(journal(), uuid.toString(), 0, Long.MAX_VALUE);
        assertEquals(1, report.sessions());
        assertTrue(report.afk() >= 50, "the AFK that went on after the reset was counted: " + report.afk());
        assertTrue(report.vanish() > 0);
        assertTrue(sessions(uuid).get(0).start() == afterReset.get(0).start(), "still the session started by the reset");
    }

    @Test
    void theJournalOfEveryoneIsWipedAfterTheConfirmation() throws Exception {
        UUID a = playerWithHistory("JournalAllA");
        UUID b = playerWithHistory("JournalAllB");
        PlayerMock online = server.addPlayer("JournalAllOnline");
        settle();

        String asked = run(console(), "playtime", "*", "reset", "journal");
        assertTrue(asked.contains("WARNING") && asked.contains("the playtime journal"), asked);
        assertEquals(1, sessions(a).size(), "nothing is deleted before the confirmation");

        String started = run(console(), "playtime", "*", "reset", "journal");
        String done = started + "\n" + String.join("\n", awaitMessages(console()));

        assertTrue(done.contains("All players' journal has been reset") && done.contains("sessions"), done);
        assertTrue(sessions(a).isEmpty());
        assertTrue(sessions(b).isEmpty());
        assertEquals(1, sessions(online.getUniqueId()).size(), "the player online has a new session going");
    }

    @Test
    void anUnknownPlayerIsReportedAndNothingIsDeleted() throws Exception {
        UUID uuid = playerWithHistory("JournalBystander2");

        String out = run(console(), "playtime", "JournalNobody", "reset", "journal");

        assertTrue(out.contains("never joined"), out);
        assertEquals(1, sessions(uuid).size());
    }

    @Test
    void resettingTheJournalNeedsThePermission() throws Exception {
        UUID uuid = playerWithHistory("JournalProtected");
        PlayerMock meddler = server.addPlayer("JournalMeddler");
        meddler.addAttachment(plugin, "playtime.others.modify", false);
        settle();

        String out = run(meddler, "playtime", "JournalProtected", "reset", "journal");

        assertTrue(out.contains("permission"), out);
        assertEquals(1, sessions(uuid).size());
    }

    @Test
    void theTypeIsSuggestedAndListedInTheHelp() throws Exception {
        server.addPlayer("JournalSuggest");
        settle();

        var command = server.getCommandMap().getCommand("playtime");
        assertEquals(List.of("journal"), command.tabComplete(console(), "playtime", new String[]{"JournalSuggest", "reset", "jou"}));
        assertTrue(command.tabComplete(console(), "playtime", new String[]{"JournalSuggest", "reset", ""}).contains("journal"));

        String out = run(console(), "playtime", "JournalSuggest", "reset", "banana");
        assertTrue(out.contains("journal"), "the list of valid types includes it: " + out);
    }

    @Test
    void afterAResetTheManagerStillHasNoSessionForAPlayerNotOnline() throws Exception {
        UUID uuid = playerWithHistory("JournalOffline");

        run(console(), "playtime", "JournalOffline", "reset", "journal");
        JournalManager.getInstance().heartbeat();
        settle();

        assertTrue(sessions(uuid).isEmpty(), "nothing was recreated for someone who is not playing");
    }
}
