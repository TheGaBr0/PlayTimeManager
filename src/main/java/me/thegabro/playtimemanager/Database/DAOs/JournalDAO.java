package me.thegabro.playtimemanager.Database.DAOs;

import me.thegabro.playtimemanager.Database.Database;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Database.Errors;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


public class JournalDAO {

    private static final DateTimeFormatter SQLITE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 9999-12-31 23:59:59.999 UTC, the last instant every supported database can store as a date. */
    private static final long MAX_DATABASE_MILLIS = 253_402_300_799_999L;

    private final DatabaseHandler dbManager;
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public JournalDAO(DatabaseHandler dbManager) {
        this.dbManager = dbManager;
    }

    /** A single parametrised write, executed as part of a batch. */
    public record Op(String sql, Object... params) {}

    public static Op insertSession(String uuid, long login) {
        return new Op("INSERT INTO playtime_journal (uuid, login, logout) VALUES (?, ?, ?)",
                uuid, login, login);
    }

    public static Op updateSessionLogout(String uuid, long login, long logout) {
        return new Op("UPDATE playtime_journal SET logout = ? WHERE uuid = ? AND login = ?",
                logout, uuid, login);
    }

    public static Op insertAfk(String uuid, long sessionLogin, long start) {
        return new Op("INSERT INTO afk_journal (uuid, session_login, afk_start, afk_end) VALUES (?, ?, ?, ?)",
                uuid, sessionLogin, start, start);
    }

    public static Op updateAfkEnd(String uuid, long start, long end) {
        return new Op("UPDATE afk_journal SET afk_end = ? WHERE uuid = ? AND afk_start = ?",
                end, uuid, start);
    }

    public static Op insertVanish(String uuid, long sessionLogin, long start) {
        return new Op("INSERT INTO vanish_journal (uuid, session_login, vanish_start, vanish_end) VALUES (?, ?, ?, ?)",
                uuid, sessionLogin, start, start);
    }

    public static Op updateVanishEnd(String uuid, long start, long end) {
        return new Op("UPDATE vanish_journal SET vanish_end = ? WHERE uuid = ? AND vanish_start = ?",
                end, uuid, start);
    }

    /**
     * Executes all operations in a single transaction, so that a session and its intervals are
     * always closed with the same timestamp or not at all.
     */
    public void executeBatch(List<Op> ops) {
        if (ops.isEmpty()) return;

        try (Connection conn = dbManager.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (Op op : ops) {
                    try (PreparedStatement ps = conn.prepareStatement(op.sql())) {
                        for (int i = 0; i < op.params().length; i++) {
                            ps.setObject(i + 1, op.params()[i]);
                        }
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
    }

    /** A closed-open time range [start, end) in epoch milliseconds, tied to a session login. */
    public record Interval(long sessionLogin, long start, long end) {}

    /** Sessions overlapping [from, to), ordered by login. sessionLogin is the login itself. */
    public List<Interval> getSessions(String uuid, long from, long to) {
        return queryIntervals(
                "SELECT login AS a, login AS b, logout AS c FROM playtime_journal " +
                        "WHERE uuid = ? AND logout > ? AND login < ? ORDER BY login",
                uuid, from, to);
    }

    public List<Interval> getAfkIntervals(String uuid, long from, long to) {
        return queryIntervals(
                "SELECT session_login AS a, afk_start AS b, afk_end AS c FROM afk_journal " +
                        "WHERE uuid = ? AND afk_end > ? AND afk_start < ? ORDER BY afk_start",
                uuid, from, to);
    }

    public List<Interval> getVanishIntervals(String uuid, long from, long to) {
        return queryIntervals(
                "SELECT session_login AS a, vanish_start AS b, vanish_end AS c FROM vanish_journal " +
                        "WHERE uuid = ? AND vanish_end > ? AND vanish_start < ? ORDER BY vanish_start",
                uuid, from, to);
    }

    private List<Interval> queryIntervals(String sql, String uuid, long from, long to) {
        List<Interval> result = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, from);
            ps.setLong(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Interval(rs.getLong("a"), rs.getLong("b"), rs.getLong("c")));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
        return result;
    }

    /** A goal or join streak reward event with its instant in epoch milliseconds. */
    public record Event(String label, long at) {}

    /** Goals completed in [from, to), ordered by completion time. */
    public List<Event> getCompletedGoals(String uuid, long from, long to) {
        return queryEvents(
                "SELECT goal_name AS evt_label, completed_at AS evt_at FROM completed_goals " +
                        "WHERE user_uuid = ? AND completed_at >= ? AND completed_at < ? ORDER BY completed_at",
                uuid, from, to);
    }

    /** Join streak rewards received in [from, to), labelled "mainInstanceID:requiredJoins". */
    public List<Event> getReceivedRewards(String uuid, long from, long to) {
        // String concatenation is not portable across the three supported databases
        String label = switch (dbManager.getDatabaseType()) {
            case MYSQL -> "CONCAT(main_instance_ID, ':', required_joins)";
            case POSTGRESQL -> "main_instance_ID::text || ':' || required_joins::text";
            default -> "main_instance_ID || ':' || required_joins";
        };
        return queryEvents(
                "SELECT " + label + " AS evt_label, received_at AS evt_at FROM received_rewards " +
                        "WHERE user_uuid = ? AND received_at >= ? AND received_at < ? ORDER BY received_at",
                uuid, from, to);
    }

    private List<Event> queryEvents(String sql, String uuid, long from, long to) {
        boolean sqlite = dbManager.getDatabaseType() == Database.DBTYPES.SQLITE;
        List<Event> result = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setObject(2, toDatabase(from, sqlite));
            ps.setObject(3, toDatabase(to, sqlite));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long at;
                    if (sqlite) {
                        at = java.time.LocalDateTime
                                .parse(rs.getString("evt_at"), SQLITE_ISO)
                                .atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
                    } else {
                        at = rs.getTimestamp("evt_at").getTime();
                    }
                    result.add(new Event(rs.getString("evt_label"), at));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        }
        return result;
    }

    /** Same representation the goal/join streak DAOs use when writing their timestamps (UTC). */
    private Object toDatabase(long epochMillis, boolean sqlite) {
        // MySQL and PostgreSQL reject dates beyond the year 9999: an open-ended range (Long.MAX_VALUE)
        // would make the whole query fail
        Instant instant = Instant.ofEpochMilli(Math.max(0, Math.min(epochMillis, MAX_DATABASE_MILLIS)));
        if (sqlite) {
            return instant.atZone(ZoneId.of("UTC")).toLocalDateTime().format(SQLITE_ISO);
        }
        return Timestamp.from(instant);
    }
}
