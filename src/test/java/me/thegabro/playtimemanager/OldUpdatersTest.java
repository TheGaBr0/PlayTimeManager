package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Updates.Version354to36Updater;
import me.thegabro.playtimemanager.Updates.Version362to363Updater;
import me.thegabro.playtimemanager.Updates.Version36to361Updater;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The database steps of the old upgrades, run on data laid out the way those old versions stored it.
 * The steps are private and normally run in sequence from the updater, so they are called directly:
 * what they do to the config files is not part of what is checked here.
 */
@Tag("updater")
class OldUpdatersTest extends PluginTestBase {

    private void sql(String... statements) throws Exception {
        try (Connection c = DatabaseHandler.getInstance().getConnection(); Statement s = c.createStatement()) {
            for (String statement : statements) s.execute(statement);
        }
    }

    private List<List<String>> rows(String query) throws Exception {
        List<List<String>> result = new ArrayList<>();
        try (Connection c = DatabaseHandler.getInstance().getConnection();
             Statement s = c.createStatement(); ResultSet rs = s.executeQuery(query)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) row.add(String.valueOf(rs.getObject(i)));
                result.add(row);
            }
        }
        return result;
    }

    private void run(Object updater, String... steps) throws Exception {
        for (String step : steps) {
            Method method = updater.getClass().getDeclaredMethod(step);
            method.setAccessible(true);
            method.invoke(updater);
        }
    }

    // ---- 3.5.4 -> 3.6: lists inside play_time become rows of their own tables -----------------------

    @Test
    void version354MovesTheListsInPlayTimeToTheirOwnTables() throws Exception {
        sql("DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed", "DELETE FROM completed_goals",
                "DROP TABLE play_time",
                "CREATE TABLE play_time (uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, " +
                        "playtime BIGINT NOT NULL, artificial_playtime BIGINT NOT NULL, " +
                        "last_seen DATETIME DEFAULT NULL, first_join DATETIME DEFAULT NULL, " +
                        "relative_join_streak INT DEFAULT 0, absolute_join_streak INT DEFAULT 0, " +
                        "received_rewards TEXT, rewards_to_be_claimed TEXT, completed_goals TEXT, PRIMARY KEY (uuid))",
                "INSERT INTO play_time VALUES ('u-alice','Alice',1200,300,'2026-01-02 03:04:05','2025-12-31 23:59:59',4,9," +
                        "'1.3, 2.5,bad,3.x', '4.2,5.7.R', 'first, second')",
                "INSERT INTO play_time VALUES ('u-bob','Bob',50,0,NULL,NULL,0,0,NULL,'','')");

        run(new Version354to36Updater(), "addAFKPlaytimeColumn", "migrateRewardData", "migrateGoalData",
                "removeOldColumns", "migrateTimestampsToInstant");

        assertEquals(List.of(List.of("u-alice", "Alice", "1", "3"), List.of("u-alice", "Alice", "2", "5")),
                rows("SELECT user_uuid, nickname, main_instance_ID, required_joins FROM received_rewards ORDER BY main_instance_ID"),
                "valid rewards are kept, malformed ones (bad, 3.x) are skipped");
        assertEquals(List.of(List.of("4", "2", "0"), List.of("5", "7", "1")),
                rows("SELECT main_instance_ID, required_joins, expired FROM rewards_to_be_claimed ORDER BY main_instance_ID"),
                "the .R suffix means the reward expired");
        assertEquals(List.of(List.of("first", "1"), List.of("second", "1")),
                rows("SELECT goal_name, received FROM completed_goals ORDER BY goal_name"),
                "old completed goals count as already received");

        List<String> columns = new ArrayList<>();
        for (List<String> column : rows("PRAGMA table_info(play_time)")) columns.add(column.get(1));
        assertEquals(List.of("uuid", "nickname", "playtime", "artificial_playtime", "afk_playtime", "last_seen",
                "first_join", "relative_join_streak", "absolute_join_streak"), columns, "the lists are gone");

        List<List<String>> alice = rows("SELECT playtime, artificial_playtime, afk_playtime, relative_join_streak, " +
                "absolute_join_streak FROM play_time WHERE uuid = 'u-alice'");
        assertEquals(List.of(List.of("1200", "300", "0", "4", "9")), alice, "numbers survive, afk time starts at 0");

        List<List<String>> bob = rows("SELECT playtime, last_seen, first_join FROM play_time WHERE uuid = 'u-bob'");
        assertEquals(List.of(List.of("50", "null", "null")), bob, "no date stays no date");
    }

    @Test
    void version354ConvertsDatesToEpochMilliseconds() throws Exception {
        sql("DROP TABLE play_time",
                "CREATE TABLE play_time (uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, " +
                        "playtime BIGINT NOT NULL, artificial_playtime BIGINT NOT NULL, afk_playtime BIGINT NOT NULL, " +
                        "last_seen DATETIME DEFAULT NULL, first_join DATETIME DEFAULT NULL, " +
                        "relative_join_streak INT DEFAULT 0, absolute_join_streak INT DEFAULT 0, PRIMARY KEY (uuid))",
                "INSERT INTO play_time VALUES ('u-d','Dated',1,0,0,'2026-01-02 03:04:05','2025-12-31 23:59:59',0,0)");

        run(new Version354to36Updater(), "migrateTimestampsToInstant");

        List<List<String>> row = rows("SELECT last_seen, first_join FROM play_time");
        long lastSeen = Long.parseLong(row.get(0).get(0));
        long firstJoin = Long.parseLong(row.get(0).get(1));
        // The old dates were written in the server's time zone
        assertEquals(Timestamp.valueOf("2026-01-02 03:04:05").getTime(), lastSeen);
        assertEquals(Timestamp.valueOf("2025-12-31 23:59:59").getTime(), firstJoin);
    }

    @Test
    void version354SurvivesAnAlreadyUpgradedDatabase() throws Exception {
        // Run on the current layout (no old columns): it must not lose or break anything
        sql("DELETE FROM play_time", "INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime, afk_playtime) " +
                "VALUES ('u-new','NewLayout',777,0,0)");

        // (migrateGoalData is left out: it reads the old completed_goals column and would only log an error)
        run(new Version354to36Updater(), "addAFKPlaytimeColumn", "migrateRewardData");

        assertEquals(List.of(List.of("777")), rows("SELECT playtime FROM play_time WHERE uuid = 'u-new'"));
    }

    // ---- 3.6 -> 3.6.1: one nickname per player in the history tables --------------------------------

    @Test
    void version36UnifiesTheNicknameOfAPlayerInTheHistoryTables() throws Exception {
        sql("DELETE FROM completed_goals", "DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g1','u-1','OldName','2026-01-01 00:00:00',1)",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g2','u-1','NewName','2026-02-01 00:00:00',1)",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g3','u-2','Other','2026-01-01 00:00:00',1)",
                "INSERT INTO received_rewards (user_uuid,nickname,main_instance_ID,required_joins,received_at) VALUES ('u-1','OldName',1,1,'2026-01-01 00:00:00')",
                "INSERT INTO received_rewards (user_uuid,nickname,main_instance_ID,required_joins,received_at) VALUES ('u-1','NewName',1,2,'2026-03-01 00:00:00')",
                "INSERT INTO rewards_to_be_claimed (user_uuid,nickname,main_instance_ID,required_joins,created_at,updated_at,expired) VALUES ('u-1','OldName',2,1,'2026-01-01 00:00:00','2026-01-01 00:00:00',0)",
                "INSERT INTO rewards_to_be_claimed (user_uuid,nickname,main_instance_ID,required_joins,created_at,updated_at,expired) VALUES ('u-1','NewName',2,2,'2026-04-01 00:00:00','2026-04-01 00:00:00',0)");

        run(new Version36to361Updater(), "fix_tables");

        assertEquals(List.of(List.of("NewName")), rows("SELECT DISTINCT nickname FROM completed_goals WHERE user_uuid = 'u-1'"));
        assertEquals(List.of(List.of("NewName")), rows("SELECT DISTINCT nickname FROM received_rewards WHERE user_uuid = 'u-1'"));
        assertEquals(List.of(List.of("NewName")), rows("SELECT DISTINCT nickname FROM rewards_to_be_claimed WHERE user_uuid = 'u-1'"));
        assertEquals(List.of(List.of("Other")), rows("SELECT nickname FROM completed_goals WHERE user_uuid = 'u-2'"),
                "players without a conflict are untouched");
    }

    // ---- 3.6.2 -> 3.6.3: dates of completed goals as text -------------------------------------------

    @Test
    void version362ConvertsMillisecondDatesToText() throws Exception {
        sql("DELETE FROM completed_goals",
                // 2026-01-01 00:00:00 UTC and 2026-01-01 00:00:09 UTC written as milliseconds, plus one already in text
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) VALUES ('ms','u-1','A','1767225600000',1,'1767225609000')",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) VALUES ('text','u-1','A','2026-05-06 07:08:09',0,NULL)");

        run(new Version362to363Updater(), "validateCompletedGoalsTimestamps");

        assertEquals(List.of(List.of("2026-01-01 00:00:00", "2026-01-01 00:00:09")),
                rows("SELECT completed_at, received_at FROM completed_goals WHERE goal_name = 'ms'"));
        assertEquals(List.of(List.of("2026-05-06 07:08:09", "null")),
                rows("SELECT completed_at, received_at FROM completed_goals WHERE goal_name = 'text'"),
                "dates already in text, and empty ones, are left alone");
    }
}
