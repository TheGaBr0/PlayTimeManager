package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Event;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Interval;
import me.thegabro.playtimemanager.Journal.JournalReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure calculation tests: no server, the DAO is replaced by fixed data. */
@Tag("journal")
class JournalReportTest {

    /** Two sessions (0-100 and 200-300), AFK and vanish intervals, plus an orphan AFK interval. */
    private static final JournalDAO DAO = new JournalDAO(null) {
        @Override public List<Interval> getSessions(String uuid, long from, long to) {
            return List.of(new Interval(0, 0, 100), new Interval(200, 200, 300));
        }
        @Override public List<Interval> getAfkIntervals(String uuid, long from, long to) {
            return List.of(new Interval(0, 10, 40), new Interval(200, 210, 250), new Interval(999, 0, 50));
        }
        @Override public List<Interval> getVanishIntervals(String uuid, long from, long to) {
            return List.of(new Interval(0, 30, 60), new Interval(200, 240, 400));
        }
        @Override public List<Event> getCompletedGoals(String uuid, long from, long to) { return List.of(); }
        @Override public List<Event> getReceivedRewards(String uuid, long from, long to) { return List.of(); }
    };

    @Test
    void fullRangeCountsEverything() {
        JournalReport r = JournalReport.build(DAO, "u", 0, 1000);
        assertEquals(2, r.sessions());
        assertEquals(200, r.online());
        assertEquals(70, r.afk());            // the orphan interval (no session) is ignored
        assertEquals(90, r.vanish());         // 30 + 60: the second one is cut at its session end (300)
        assertEquals(20, r.afkWhileVanished()); // 30-40 and 240-250
        assertEquals(110, r.onlineVisible());
        assertEquals(50, r.afkVisible());
    }

    @Test
    void partialRangeIsExactNotProportional() {
        JournalReport r = JournalReport.build(DAO, "u", 50, 220);
        assertEquals(70, r.online());  // 50-100 and 200-220
        assertEquals(10, r.afk());     // only 210-220 falls in the range
        assertEquals(10, r.vanish());  // only 50-60
        assertEquals(0, r.afkWhileVanished());
    }
}
