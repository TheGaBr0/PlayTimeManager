package me.thegabro.playtimemanager.Journal;

import me.thegabro.playtimemanager.Database.DAOs.JournalDAO;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Event;
import me.thegabro.playtimemanager.Database.DAOs.JournalDAO.Interval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a player did in a time range [from, to): how long they were online, AFK and vanished
 * (exact, computed from the journal intervals), plus the goals and join streak rewards they got.
 */
public record JournalReport(
        int sessions,
        long online,
        long vanish,
        long afk,
        long afkWhileVanished,
        List<Event> goals,
        List<Event> rewards
) {

    public long onlineVisible() {
        return online - vanish;
    }

    public long afkVisible() {
        return afk - afkWhileVanished;
    }

    public static JournalReport build(JournalDAO dao, String uuid, long from, long to) {
        return compute(dao.getSessions(uuid, from, to),
                dao.getAfkIntervals(uuid, from, to),
                dao.getVanishIntervals(uuid, from, to),
                dao.getCompletedGoals(uuid, from, to),
                dao.getReceivedRewards(uuid, from, to),
                from, to);
    }

    /**
     * Reports of every player that has a session in [from, to), with three queries in total.
     * Goals and rewards are not loaded (empty lists).
     */
    public static Map<String, JournalReport> buildAll(JournalDAO dao, long from, long to) {
        Map<String, List<Interval>> sessions = dao.getAllSessions(from, to);
        Map<String, List<Interval>> afk = dao.getAllAfkIntervals(from, to);
        Map<String, List<Interval>> vanish = dao.getAllVanishIntervals(from, to);

        Map<String, JournalReport> result = new HashMap<>();
        for (Map.Entry<String, List<Interval>> entry : sessions.entrySet()) {
            String uuid = entry.getKey();
            result.put(uuid, compute(entry.getValue(),
                    afk.getOrDefault(uuid, List.of()),
                    vanish.getOrDefault(uuid, List.of()),
                    List.of(), List.of(), from, to));
        }
        return result;
    }

    private static JournalReport compute(List<Interval> rawSessions, List<Interval> rawAfk, List<Interval> rawVanish,
                                         List<Event> goals, List<Event> rewards, long from, long to) {
        List<Interval> sessions = clampToRange(rawSessions, from, to);

        // Real session bounds, used to keep AFK/vanish intervals inside their session
        Map<Long, Interval> sessionByLogin = new HashMap<>();
        for (Interval session : rawSessions) {
            sessionByLogin.put(session.sessionLogin(), session);
        }

        List<Interval> afk = clampToSessions(rawAfk, sessionByLogin, from, to);
        List<Interval> vanish = clampToSessions(rawVanish, sessionByLogin, from, to);

        return new JournalReport(
                sessions.size(),
                total(sessions),
                total(vanish),
                total(afk),
                overlap(afk, vanish),
                goals,
                rewards
        );
    }

    private static List<Interval> clampToRange(List<Interval> intervals, long from, long to) {
        List<Interval> result = new ArrayList<>();
        for (Interval i : intervals) {
            long start = Math.max(i.start(), from);
            long end = Math.min(i.end(), to);
            if (end > start) result.add(new Interval(i.sessionLogin(), start, end));
        }
        return result;
    }

    private static List<Interval> clampToSessions(List<Interval> intervals, Map<Long, Interval> sessions,
                                                  long from, long to) {
        List<Interval> result = new ArrayList<>();
        for (Interval i : intervals) {
            Interval session = sessions.get(i.sessionLogin());
            if (session == null) continue;
            long start = Math.max(Math.max(i.start(), session.start()), from);
            long end = Math.min(Math.min(i.end(), session.end()), to);
            if (end > start) result.add(new Interval(i.sessionLogin(), start, end));
        }
        return result;
    }

    private static long total(List<Interval> intervals) {
        long sum = 0;
        for (Interval i : intervals) sum += i.end() - i.start();
        return sum;
    }

    /** Total time covered by both lists. Each list must be sorted and made of disjoint intervals. */
    private static long overlap(List<Interval> a, List<Interval> b) {
        long sum = 0;
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            long start = Math.max(a.get(i).start(), b.get(j).start());
            long end = Math.min(a.get(i).end(), b.get(j).end());
            if (end > start) sum += end - start;

            if (a.get(i).end() < b.get(j).end()) i++; else j++;
        }
        return sum;
    }
}
