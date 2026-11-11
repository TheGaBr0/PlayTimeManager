package me.thegabro.playtimemanager.Web;

import me.thegabro.playtimemanager.Database.DatabaseHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Read-only queries for the web dashboard. Only parameterized statements are used;
 * the few dynamic fragments (ORDER BY) come from fixed whitelists.
 */
public class WebDAO {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "nickname", "nickname",
            "playtime", "playtime",
            "afk", "afk_playtime",
            "artificial", "artificial_playtime",
            "lastSeen", "last_seen",
            "firstJoin", "first_join",
            "streak", "relative_join_streak");

    private static final String PLAYER_COLS =
            "uuid, nickname, playtime, artificial_playtime, afk_playtime, last_seen, first_join, " +
                    "relative_join_streak, absolute_join_streak";

    private final DatabaseHandler db;

    public WebDAO(DatabaseHandler db) { this.db = db; }

    private Map<String, Object> playerRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", rs.getString(1));
        m.put("nickname", rs.getString(2));
        m.put("playtime", rs.getLong(3));
        m.put("artificial", rs.getLong(4));
        m.put("afk", rs.getLong(5));
        m.put("lastSeen", nullableLong(rs, 6));
        m.put("firstJoin", nullableLong(rs, 7));
        m.put("relativeStreak", rs.getInt(8));
        m.put("absoluteStreak", rs.getInt(9));
        return m;
    }

    private static Long nullableLong(ResultSet rs, int i) throws SQLException {
        long v = rs.getLong(i);
        return rs.wasNull() ? null : v;
    }

    public Map<String, Object> overview() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(playtime),0), COALESCE(SUM(afk_playtime),0), " +
                            "COALESCE(SUM(artificial_playtime),0) FROM play_time");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                out.put("players", rs.getLong(1));
                out.put("totalPlaytime", rs.getLong(2));
                out.put("totalAfk", rs.getLong(3));
                out.put("totalArtificial", rs.getLong(4));
            }
            List<Map<String, Object>> top = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + PLAYER_COLS + " FROM play_time ORDER BY playtime DESC LIMIT 10");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) top.add(playerRow(rs));
            }
            out.put("top", top);

            // Distinct players per day over the last 30 days, bucketed in Java for portability
            long dayMs = 86_400_000L;
            long today = System.currentTimeMillis() / dayMs;
            long since = (today - 29) * dayMs;
            Map<Long, Set<String>> perDay = new TreeMap<>();
            for (long d = today - 29; d <= today; d++) perDay.put(d, new HashSet<>());
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid, login FROM playtime_journal WHERE login >= ?")) {
                ps.setLong(1, since);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Set<String> s = perDay.get(rs.getLong(2) / dayMs);
                        if (s != null) s.add(rs.getString(1));
                    }
                }
            }
            List<Map<String, Object>> days = new ArrayList<>();
            perDay.forEach((d, s) -> days.add(Map.of("day", d * dayMs, "players", s.size())));
            out.put("activity", days);
            out.put("journal", journalStats(c, today, dayMs));
        }
        return out;
    }

    private static final long SLOT_MS = 900_000L;      // 15 minutes
    private static final int WEEK_SLOTS = 7 * 24 * 4;   // slot 0 = Thursday 00:00 UTC (the epoch)
    private static final long[] LENGTH_LIMITS_MIN = {5, 15, 30, 60, 120, 240};

    /** Journal summaries of the last 30 days. Finished sessions only (an open one has no length yet). */
    private Map<String, Object> journalStats(Connection c, long today, long dayMs) throws SQLException {
        long since = (today - 29) * dayMs;
        long sessions = 0, totalMs = 0, longestMs = 0, longestAt = 0;
        String longestBy = null;
        long[] lengths = new long[LENGTH_LIMITS_MIN.length + 1];
        long[] heat = new long[WEEK_SLOTS];
        long[] perDay = new long[30];
        Set<String> players = new HashSet<>();
        List<long[]> events = new ArrayList<>(); // {time, +1/-1}

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT j.uuid, p.nickname, j.login, j.logout FROM playtime_journal j " +
                        "LEFT JOIN play_time p ON p.uuid = j.uuid WHERE j.login >= ? AND j.logout > j.login")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long login = rs.getLong(3), logout = rs.getLong(4), len = logout - login;
                    sessions++;
                    totalMs += len;
                    players.add(rs.getString(1));
                    if (len > longestMs) { longestMs = len; longestBy = rs.getString(2); longestAt = login; }
                    int b = 0;
                    while (b < LENGTH_LIMITS_MIN.length && len >= LENGTH_LIMITS_MIN[b] * 60_000L) b++;
                    lengths[b]++;
                    heat[(int) ((login / SLOT_MS) % WEEK_SLOTS)]++;
                    int d = (int) (login / dayMs - (today - 29));
                    if (d >= 0 && d < 30) perDay[d] += len;
                    events.add(new long[]{login, 1});
                    events.add(new long[]{logout, -1});
                }
            }
        }

        // Peak of players online at the same time (a logout sorts before a login at the same instant)
        events.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        int cur = 0, peak = 0;
        long peakAt = 0;
        for (long[] e : events) {
            cur += (int) e[1];
            if (cur > peak) { peak = cur; peakAt = e[0]; }
        }

        long afkMs = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(afk_end - afk_start), 0) FROM afk_journal " +
                        "WHERE session_login >= ? AND afk_end > afk_start")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); afkMs = rs.getLong(1); }
        }
        long newPlayers = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM play_time WHERE first_join >= ?")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); newPlayers = rs.getLong(1); }
        }

        Map<String, Object> j = new LinkedHashMap<>();
        j.put("sessions", sessions);
        j.put("totalMs", totalMs);
        j.put("avgMs", sessions == 0 ? 0 : totalMs / sessions);
        j.put("afkMs", Math.min(afkMs, totalMs));
        j.put("activePlayers", players.size());
        j.put("newPlayers", newPlayers);
        j.put("longestMs", longestMs);
        if (longestBy != null) j.put("longestBy", longestBy);
        j.put("longestAt", longestAt);
        j.put("peakPlayers", peak);
        j.put("peakAt", peakAt);
        List<Long> lens = new ArrayList<>(); for (long v : lengths) lens.add(v);
        j.put("lengths", lens);                      // buckets: <5m <15m <30m <1h <2h <4h 4h+
        List<Long> h = new ArrayList<>(); for (long v : heat) h.add(v);
        j.put("heat", h);                            // 672 slots of 15 min, UTC, slot 0 = Thursday 00:00
        List<Long> pd = new ArrayList<>(); for (long v : perDay) pd.add(v);
        j.put("onlinePerDay", pd);                   // ms per day, same 30 days as "activity"
        return j;
    }

    public Map<String, Object> players(String search, String sort, boolean desc, int page, int size) throws SQLException {
        String col = SORT_COLUMNS.getOrDefault(sort, "playtime");
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 0);
        boolean hasSearch = search != null && !search.isBlank();
        String where = hasSearch ? " WHERE LOWER(nickname) LIKE ? ESCAPE '!'" : "";
        String like = hasSearch
                ? "%" + search.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%" : null;

        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM play_time" + where)) {
                if (hasSearch) ps.setString(1, like);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); out.put("total", rs.getLong(1)); }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            // NULLs (never seen) always sink to the bottom regardless of direction
            String order = col + (desc ? " DESC" : " ASC");
            try (PreparedStatement ps = c.prepareStatement("SELECT " + PLAYER_COLS + " FROM play_time" + where +
                    " ORDER BY " + order + ", nickname ASC LIMIT ? OFFSET ?")) {
                int i = 1;
                if (hasSearch) ps.setString(i++, like);
                ps.setInt(i++, size);
                ps.setInt(i, page * size);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(playerRow(rs)); }
            }
            out.put("rows", rows);
            out.put("page", page);
            out.put("size", size);
        }
        return out;
    }

    /** Players with an open journal session (logout not after login), longest-connected first. */
    public List<Map<String, Object>> online() throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT p.uuid, p.nickname, p.playtime, p.artificial_playtime, " +
                     "p.afk_playtime, p.last_seen, p.first_join, p.relative_join_streak, p.absolute_join_streak, MIN(j.login) " +
                     "FROM play_time p JOIN playtime_journal j ON j.uuid = p.uuid WHERE j.logout <= j.login " +
                     "GROUP BY p.uuid ORDER BY MIN(j.login) ASC, p.nickname ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = playerRow(rs);
                m.put("since", rs.getLong(10));
                rows.add(m);
            }
        }
        return rows;
    }

    public Map<String, Object> player(String uuid) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + PLAYER_COLS + " FROM play_time WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    out.putAll(playerRow(rs));
                }
            }

            out.put("goals", list(c,
                    "SELECT goal_name, completed_at, received, received_at FROM completed_goals " +
                            "WHERE user_uuid = ? ORDER BY id DESC LIMIT 500", uuid,
                    rs -> Map.of("name", rs.getString(1), "completedAt", str(rs, 2),
                            "received", rs.getInt(3) == 1, "receivedAt", str(rs, 4))));

            out.put("receivedRewards", list(c,
                    "SELECT main_instance_ID, required_joins, received_at FROM received_rewards " +
                            "WHERE user_uuid = ? ORDER BY id DESC LIMIT 500", uuid,
                    rs -> Map.of("rewardId", rs.getInt(1), "joins", rs.getInt(2), "at", str(rs, 3))));

            out.put("pendingRewards", list(c,
                    "SELECT main_instance_ID, required_joins, created_at, expired FROM rewards_to_be_claimed " +
                            "WHERE user_uuid = ? ORDER BY id DESC LIMIT 500", uuid,
                    rs -> Map.of("rewardId", rs.getInt(1), "joins", rs.getInt(2), "at", str(rs, 3),
                            "expired", rs.getInt(4) == 1)));

            List<Map<String, Object>> sessions = list(c,
                    "SELECT login, logout FROM playtime_journal WHERE uuid = ? ORDER BY login DESC LIMIT 200", uuid,
                    rs -> Map.of("login", rs.getLong(1), "logout", rs.getLong(2)));
            out.put("sessions", sessions);

            long oldest = sessions.isEmpty() ? Long.MAX_VALUE : (Long) sessions.get(sessions.size() - 1).get("login");
            out.put("afkIntervals", intervals(c, "SELECT session_login, afk_start, afk_end FROM afk_journal " +
                    "WHERE uuid = ? AND session_login >= ? ORDER BY afk_start", uuid, oldest));
            out.put("vanishIntervals", intervals(c, "SELECT session_login, vanish_start, vanish_end FROM vanish_journal " +
                    "WHERE uuid = ? AND session_login >= ? ORDER BY vanish_start", uuid, oldest));
        }
        return out;
    }

    /** Completion counts per goal name: {completions, distinct players}. */
    public Map<String, long[]> goalCounts() throws SQLException {
        Map<String, long[]> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT goal_name, COUNT(*), COUNT(DISTINCT user_uuid) FROM completed_goals GROUP BY goal_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), new long[]{rs.getLong(2), rs.getLong(3)});
        }
        return out;
    }

    /** Who completed a goal, newest first. */
    public List<Map<String, Object>> goalCompletions(String goal) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_uuid, nickname, completed_at FROM completed_goals WHERE goal_name = ? " +
                             "ORDER BY id DESC LIMIT 1000")) {
            ps.setString(1, goal);
            List<Map<String, Object>> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Map.of("uuid", rs.getString(1), "nickname", rs.getString(2), "at", str(rs, 3)));
            }
            return out;
        }
    }

    public Map<String, Object> rewards() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = db.getConnection()) {
            out.put("rewards", list(c,
                    "SELECT main_instance_ID, required_joins, COUNT(*) FROM received_rewards " +
                            "GROUP BY main_instance_ID, required_joins ORDER BY main_instance_ID, required_joins", null,
                    rs -> Map.of("rewardId", rs.getInt(1), "joins", rs.getInt(2), "received", rs.getLong(3))));
            out.put("pending", list(c,
                    "SELECT nickname, main_instance_ID, required_joins, created_at FROM rewards_to_be_claimed " +
                            "WHERE expired = 0 ORDER BY id DESC LIMIT 500", null,
                    rs -> Map.of("nickname", rs.getString(1), "rewardId", rs.getInt(2), "joins", rs.getInt(3),
                            "at", str(rs, 4))));
        }
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private interface RowMapper { Map<String, Object> map(ResultSet rs) throws SQLException; }

    private static String str(ResultSet rs, int i) throws SQLException {
        String s = rs.getString(i);
        return s == null ? "" : s;
    }

    private List<Map<String, Object>> list(Connection c, String sql, String uuid, RowMapper mapper) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (uuid != null) ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(mapper.map(rs)); }
        }
        return out;
    }

    private List<Map<String, Object>> intervals(Connection c, String sql, String uuid, long oldest) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, oldest);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Map.of("session", rs.getLong(1), "start", rs.getLong(2), "end", rs.getLong(3)));
            }
        }
        return out;
    }
}
