package me.thegabro.playtimemanager.Database;

import java.util.List;

/**
 * DDL of the playtime journal tables, shared by the three database implementations, the version
 * updater and the database migration so that they can never drift apart.
 */
public final class JournalTables {

    public static final String PLAYTIME_JOURNAL = "playtime_journal";
    public static final String AFK_JOURNAL = "afk_journal";
    public static final String VANISH_JOURNAL = "vanish_journal";

    public static final List<String> NAMES = List.of(PLAYTIME_JOURNAL, AFK_JOURNAL, VANISH_JOURNAL);

    private JournalTables() {}

    public static List<String> createStatements(Database.DBTYPES type) {
        String tail = type == Database.DBTYPES.MYSQL
                ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                : ")";

        return List.of(
                "CREATE TABLE IF NOT EXISTS " + PLAYTIME_JOURNAL + " (" +
                        "uuid VARCHAR(36) NOT NULL," +
                        "login BIGINT NOT NULL," +
                        "logout BIGINT NOT NULL," +
                        "PRIMARY KEY (uuid, login)" + tail,
                "CREATE TABLE IF NOT EXISTS " + AFK_JOURNAL + " (" +
                        "uuid VARCHAR(36) NOT NULL," +
                        "session_login BIGINT NOT NULL," +
                        "afk_start BIGINT NOT NULL," +
                        "afk_end BIGINT NOT NULL," +
                        "PRIMARY KEY (uuid, afk_start)" + tail,
                "CREATE TABLE IF NOT EXISTS " + VANISH_JOURNAL + " (" +
                        "uuid VARCHAR(36) NOT NULL," +
                        "session_login BIGINT NOT NULL," +
                        "vanish_start BIGINT NOT NULL," +
                        "vanish_end BIGINT NOT NULL," +
                        "PRIMARY KEY (uuid, vanish_start)" + tail
        );
    }
}
