package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.Updates.Version31to32Updater;
import me.thegabro.playtimemanager.Updates.Version321to33Updater;
import me.thegabro.playtimemanager.Updates.Version332to34Updater;
import me.thegabro.playtimemanager.Updates.Version341to342Updater;
import me.thegabro.playtimemanager.Updates.Version342to35Updater;
import me.thegabro.playtimemanager.Updates.Version34to341Updater;
import me.thegabro.playtimemanager.Updates.Version351to352Updater;
import me.thegabro.playtimemanager.Updates.Version354to36Updater;
import me.thegabro.playtimemanager.Updates.Version362to363Updater;
import me.thegabro.playtimemanager.Updates.Version363to364Updater;
import me.thegabro.playtimemanager.Updates.Version364to365Updater;
import me.thegabro.playtimemanager.Updates.Version365to366Updater;
import me.thegabro.playtimemanager.Updates.Version36to361Updater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every upgrade step, run through its public performUpgrade() on what the previous version left behind:
 * the database layout, the config files and the folders of that version.
 */
@Tag("updater")
class UpdatersTest extends PluginTestBase {

    // ---- helpers -------------------------------------------------------------------------------

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

    private List<String> columnsOfPlayTime() throws Exception {
        List<String> columns = new ArrayList<>();
        for (List<String> column : rows("PRAGMA table_info(play_time)")) columns.add(column.get(1));
        return columns;
    }

    private File dataFile(String relativePath) {
        return new File(plugin.getDataFolder(), relativePath);
    }

    private void assertConfigIsCurrent() {
        assertEquals(plugin.getConfigVersion(), plugin.getConfiguration().getString("config-version", "?"),
                "config.yml was brought to the current version");
    }

    /** The layout of play_time before the columns the next upgrade adds. */
    private void legacyPlayTime(String columns, String... inserts) throws Exception {
        sql("DROP TABLE IF EXISTS play_time", "CREATE TABLE play_time (" + columns + ")");
        sql(inserts);
    }

    // ---- 3.1 -> 3.2 ----------------------------------------------------------------------------

    @Test
    void from31MergesDuplicatesAndAddsLastSeen() throws Exception {
        legacyPlayTime("uuid VARCHAR(32) NOT NULL, nickname VARCHAR(32) NOT NULL, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, completed_goals TEXT DEFAULT ''",
                "INSERT INTO play_time VALUES ('u1','Alice',100,10,'a,b')",
                "INSERT INTO play_time VALUES ('u1','Alice',50,5,'b,c')",
                "INSERT INTO play_time VALUES ('u2','Bob',7,0,'')");

        new Version31to32Updater().performUpgrade();

        assertEquals(List.of(List.of("u1", "Alice", "150", "15", "a,b,c"), List.of("u2", "Bob", "7", "0", "")),
                rows("SELECT uuid, nickname, playtime, artificial_playtime, completed_goals FROM play_time ORDER BY uuid"),
                "the two Alice rows become one: times add up, goals are merged without repeats");
        assertTrue(columnsOfPlayTime().contains("last_seen"), "last_seen is added");
        assertThrows(SQLException.class, () -> sql("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime) " +
                "VALUES ('u1','Other',1,0)"), "uuid is unique from now on");
        assertThrows(SQLException.class, () -> sql("INSERT INTO play_time (uuid, nickname, playtime, artificial_playtime) " +
                "VALUES ('u9','Bob',1,0)"), "so is the nickname");
        assertConfigIsCurrent();
    }

    // ---- 3.2.1 -> 3.3 and 3.3.2 -> 3.4: new columns --------------------------------------------

    @Test
    void from321AddsFirstJoin() throws Exception {
        legacyPlayTime("uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, completed_goals TEXT DEFAULT '', last_seen DATETIME DEFAULT NULL",
                "INSERT INTO play_time VALUES ('u1','Alice',100,10,'a',NULL)");

        new Version321to33Updater().performUpgrade();

        assertTrue(columnsOfPlayTime().contains("first_join"));
        assertEquals(List.of(List.of("100", "null")), rows("SELECT playtime, first_join FROM play_time"),
                "existing rows are kept, with no first join recorded");
        assertConfigIsCurrent();
    }

    @Test
    void from332AddsTheJoinStreakAndRewardColumns() throws Exception {
        legacyPlayTime("uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, completed_goals TEXT DEFAULT '', last_seen DATETIME DEFAULT NULL, " +
                        "first_join DATETIME DEFAULT NULL",
                "INSERT INTO play_time VALUES ('u1','Alice',100,10,'a',NULL,NULL)");

        new Version332to34Updater().performUpgrade();

        assertTrue(columnsOfPlayTime().containsAll(List.of("relative_join_streak", "absolute_join_streak",
                "received_rewards", "rewards_to_be_claimed")));
        assertEquals(List.of(List.of("0", "0", "", "")),
                rows("SELECT relative_join_streak, absolute_join_streak, received_rewards, rewards_to_be_claimed FROM play_time"),
                "existing players start with no streak and no rewards");
        assertConfigIsCurrent();
    }

    // ---- 3.4 -> 3.4.1: the goal files change shape ---------------------------------------------

    @Test
    void from34RewritesTheGoalFilesInTheNewShape() throws Exception {
        File goal = dataFile("Goals/oldgoal.yml");
        goal.getParentFile().mkdirs();
        Files.writeString(goal.toPath(), String.join("\n",
                "active: true",
                "time: 7200",
                "goal-message: \"Well done %PLAYER_NAME%\"",
                "goal-sound: ENTITY_PLAYER_LEVELUP",
                "permissions:", "  - reward.permission",
                "commands:", "  - say hello PLAYER_NAME", ""));

        new Version34to341Updater().performUpgrade();

        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(goal);
        assertEquals(7200, upgraded.getLong("requirements.time"));
        assertEquals(List.of("reward.permission"), upgraded.getStringList("rewards.permissions"));
        assertEquals(List.of("say hello PLAYER_NAME"), upgraded.getStringList("rewards.commands"));
        assertEquals("Well done %PLAYER_NAME%", upgraded.getString("goal-message"));
        assertTrue(upgraded.getBoolean("active"));
        assertFalse(upgraded.contains("time"), "the old keys are gone");
        assertFalse(upgraded.contains("commands"));
        assertConfigIsCurrent();
    }

    // ---- 3.4.1 -> 3.4.2 and 3.6.4 -> 3.6.5: config only ----------------------------------------

    @Test
    void from341RecreatesTheConfigKeepingTheUsersValues() throws Exception {
        plugin.getConfiguration().set("config-version", "3.6");
        plugin.getConfiguration().set("check-for-updates", false);

        new Version341to342Updater().performUpgrade();

        assertConfigIsCurrent();
        assertFalse(plugin.getConfiguration().getBoolean("check-for-updates", true), "a custom value survives");
    }

    @Test
    void from364RecreatesTheConfigKeepingTheUsersValues() throws Exception {
        plugin.getConfiguration().set("config-version", "4.3");
        plugin.getConfiguration().set("month-value-in-days", 28);

        new Version364to365Updater().performUpgrade();

        assertConfigIsCurrent();
        assertEquals(28, plugin.getConfiguration().getInt("month-value-in-days", 30), "a custom value survives");
    }

    // ---- 3.4.2 -> 3.5: Translations becomes Customizations --------------------------------------

    @Test
    void from342MovesTranslationsToCustomizationsAndKeepsThePlaytimeMessages() throws Exception {
        plugin.getConfiguration().set("playtime-self-message", "Your time is %PLAYTIME%");
        plugin.getConfiguration().set("playtime-others-message", "%PLAYER_NAME% has %PLAYTIME%");
        Path translations = dataFile("Translations").toPath();
        Files.createDirectories(translations.resolve("Commands"));
        Files.writeString(translations.resolve("Commands/legacy.yml"), "custom: translated\n");

        new Version342to35Updater().performUpgrade();

        assertFalse(Files.exists(translations), "Translations is gone");
        assertTrue(Files.exists(dataFile("Customizations/Commands/legacy.yml").toPath()), "its files moved to Customizations");
        CommandsConfiguration commands = CommandsConfiguration.getInstance();
        assertEquals("Your time is %PLAYTIME%", commands.getString("playtime-self-message"));
        assertEquals("%PLAYER_NAME% has %PLAYTIME%", commands.getString("playtime-others-message"));
    }

    @Test
    void from342CreatesCustomizationsWhenThereWasNoTranslationsFolder() throws Exception {
        assertFalse(Files.exists(dataFile("Translations").toPath()));

        new Version342to35Updater().performUpgrade();

        assertTrue(Files.isDirectory(dataFile("Customizations").toPath()));
    }

    // ---- 3.5.1 -> 3.5.2: afk column and {placeholders} -> %PLACEHOLDERS% -------------------------

    @Test
    void from351AddsAfkTimeAndRewritesTheGuiPlaceholders() throws Exception {
        legacyPlayTime("uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, last_seen DATETIME DEFAULT NULL, first_join DATETIME DEFAULT NULL, " +
                        "relative_join_streak INT DEFAULT 0, absolute_join_streak INT DEFAULT 0",
                "INSERT INTO play_time VALUES ('u1','Alice',100,10,NULL,NULL,2,5)");
        GUIsConfiguration guis = GUIsConfiguration.getInstance();
        guis.set("rewards-gui.messages.not-available", "Page {current_page} of {total_pages}: {count} rewards");

        new Version351to352Updater().performUpgrade();

        assertTrue(columnsOfPlayTime().contains("afk_playtime"));
        assertEquals(List.of(List.of("100", "0", "2", "5")),
                rows("SELECT playtime, afk_playtime, relative_join_streak, absolute_join_streak FROM play_time"));
        assertEquals("Page %CURRENT_PAGE% of %TOTAL_PAGES%: %COUNT% rewards",
                guis.getString("rewards-gui.messages.not-available"), "old {placeholders} use the new spelling");
        assertConfigIsCurrent();
    }

    // ---- 3.5.4 -> 3.6: lists become tables, dates become numbers --------------------------------

    @Test
    void from354MovesEverythingAndKeepsThePlaytimeMessages() throws Exception {
        plugin.getConfiguration().set("goal-check-rate", 300L);
        CommandsConfiguration commands = CommandsConfiguration.getInstance();
        commands.set("playtime-self-message", "[§6PlayTime§eManager§f]§7 Your playtime is §6%PLAYTIME%");
        commands.set("playtime-others-message", "[§6PlayTime§eManager§f]§7 %PLAYER_NAME% has §6%PLAYTIME%");
        sql("DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed", "DELETE FROM completed_goals");
        legacyPlayTime("uuid VARCHAR(32) NOT NULL UNIQUE, nickname VARCHAR(32) NOT NULL UNIQUE, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, last_seen DATETIME DEFAULT NULL, first_join DATETIME DEFAULT NULL, " +
                        "relative_join_streak INT DEFAULT 0, absolute_join_streak INT DEFAULT 0, " +
                        "received_rewards TEXT, rewards_to_be_claimed TEXT, completed_goals TEXT",
                "INSERT INTO play_time VALUES ('u1','Alice',1200,300,'2026-01-02 03:04:05',NULL,4,9,'1.3','2.5.R','first')");

        new Version354to36Updater().performUpgrade();

        assertEquals(List.of(List.of("1", "3")), rows("SELECT main_instance_ID, required_joins FROM received_rewards"));
        assertEquals(List.of(List.of("2", "5", "1")), rows("SELECT main_instance_ID, required_joins, expired FROM rewards_to_be_claimed"));
        assertEquals(List.of(List.of("first")), rows("SELECT goal_name FROM completed_goals"));
        assertFalse(columnsOfPlayTime().contains("received_rewards"), "the lists are no longer columns");
        assertEquals(List.of(List.of("1200", "300", "0")), rows("SELECT playtime, artificial_playtime, afk_playtime FROM play_time"));
        String self = CommandsConfiguration.getInstance().getString("playtime.self-message");
        assertNotNull(self, "the playtime message moved to its new place");
        assertTrue(self.contains("Your playtime is") && !self.contains("§"), "with & colour codes: " + self);
    }

    // ---- 3.6 -> 3.6.1 and 3.6.2 -> 3.6.3: data fixes ---------------------------------------------

    @Test
    void from36UnifiesNicknamesAndBringsTheConfigUpToDate() throws Exception {
        sql("DELETE FROM completed_goals", "DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g1','u-1','OldName','2026-01-01 00:00:00',1)",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received) VALUES ('g2','u-1','NewName','2026-02-01 00:00:00',1)");

        new Version36to361Updater().performUpgrade();

        assertEquals(List.of(List.of("NewName")), rows("SELECT DISTINCT nickname FROM completed_goals WHERE user_uuid = 'u-1'"));
        assertConfigIsCurrent();
    }

    @Test
    void from362ConvertsDatesAndTurnsOnDistributeRemovedTime() throws Exception {
        sql("DELETE FROM completed_goals", "DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed",
                "INSERT INTO completed_goals (goal_name,user_uuid,nickname,completed_at,received,received_at) " +
                        "VALUES ('ms','u-1','A','1767225600000',1,'1767225609000')");

        new Version362to363Updater().performUpgrade();

        assertEquals(List.of(List.of("2026-01-01 00:00:00", "2026-01-01 00:00:09")),
                rows("SELECT completed_at, received_at FROM completed_goals"));
        String formats = Files.readString(dataFile("Customizations/PlaytimeFormats/default.yml").toPath());
        assertTrue(formats.contains("distribute-removed-time: true"), "the format option is on:\n" + formats);
        assertConfigIsCurrent();
    }

    // ---- 3.6.3 -> 3.6.4: rewards become repeatable, the rewards GUI settings move ------------------

    @Test
    void from363MakesRewardsRepeatableAndMovesTheGuiSettings() throws Exception {
        RewardRegistry.getInstance().clearRewards();
        JoinStreakReward reward = new JoinStreakReward(950, 4);
        reward.setRepeatable(false);
        RewardRegistry.getInstance().addReward(reward);
        GUIsConfiguration guis = GUIsConfiguration.getInstance();
        guis.set("rewards-gui.gui.border-item-name", "&8Border");
        guis.set("rewards-gui.pagination.page-indicator", "&7Page");

        new Version363to364Updater().performUpgrade();

        assertEquals("&8Border", guis.getString("rewards-gui.gui.border.name"), "the border name moved");
        assertEquals("&7Page", guis.getString("rewards-gui.pagination.page-indicator.name"), "so did the page indicator");
        assertFalse(RewardRegistry.getInstance().getRewards().isEmpty());
        assertTrue(RewardRegistry.getInstance().getRewards().stream().allMatch(JoinStreakReward::isRepeatable),
                "every reward is repeatable now");
        assertTrue(YamlConfiguration.loadConfiguration(dataFile("Rewards/950.yml")).getBoolean("repeatable"));
    }

    // ---- 3.6.5 -> 3.6.6: goals gain the per-player option -----------------------------------------

    @Test
    void from365RewritesTheGoalsKeepingTheirSettings() throws Exception {
        File goal = dataFile("Goals/pregoal.yml");
        goal.getParentFile().mkdirs();
        Files.writeString(goal.toPath(), String.join("\n",
                "active: false",
                "repeatable: true",
                "check-time-interval: \"600\"",
                "goal-message: \"Custom message\"",
                "requirements:", "  time: 9000", "  permissions: []", "  placeholders: []",
                "rewards:", "  permissions: []", "  commands:", "    - say custom", ""));
        assertFalse(YamlConfiguration.loadConfiguration(goal).contains("per-player-check"), "setup: the old shape");

        new Version365to366Updater().performUpgrade();

        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(goal);
        assertTrue(upgraded.contains("per-player-check"), "the new option is there");
        assertFalse(upgraded.getBoolean("per-player-check"), "off by default");
        assertEquals(9000, upgraded.getLong("requirements.time"));
        assertEquals("600", upgraded.getString("check-time-interval"));
        assertEquals("Custom message", upgraded.getString("goal-message"));
        assertTrue(upgraded.getBoolean("repeatable"));
        assertEquals(List.of("say custom"), upgraded.getStringList("rewards.commands"));
        assertConfigIsCurrent();
    }
}
