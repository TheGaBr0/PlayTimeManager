package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Updates.UpdateManager;
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
import me.thegabro.playtimemanager.Updates.Version366to400Updater;
import me.thegabro.playtimemanager.Updates.Version36to361Updater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chain of upgrades: whoever starts from an old config version must go through every later upgrade,
 * in order, up to the current one.
 */
@Tag("updater")
class UpdateChainTest extends PluginTestBase {

    /** Every upgrade, oldest first, with the config version it starts from. */
    private static final List<String> VERSIONS = List.of("3.2", "3.3", "3.4", "3.5", "3.6", "3.7", "3.8", "3.9",
            "4.0", "4.1", "4.2", "4.3", "4.4", "4.5");
    private static final List<Class<?>> ALL_UPDATERS = List.of(
            Version31to32Updater.class, Version321to33Updater.class, Version332to34Updater.class,
            Version34to341Updater.class, Version341to342Updater.class, Version342to35Updater.class,
            Version351to352Updater.class, Version354to36Updater.class, Version36to361Updater.class,
            Version362to363Updater.class, Version363to364Updater.class, Version364to365Updater.class,
            Version365to366Updater.class, Version366to400Updater.class);

    // ---- which upgrades run -------------------------------------------------------------------

    @TestFactory
    List<DynamicTest> everyStartingVersionRunsEveryLaterUpgradeInOrder() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < VERSIONS.size(); i++) {
            String version = VERSIONS.get(i);
            List<Class<?>> expected = ALL_UPDATERS.subList(i, ALL_UPDATERS.size());
            tests.add(DynamicTest.dynamicTest("from config " + version, () ->
                    assertEquals(expected.stream().map(Class::getSimpleName).toList(),
                            UpdateManager.updatersFrom(version).stream().map(Class::getSimpleName).toList(),
                            "upgrades run from config version " + version)));
        }
        return tests;
    }

    @Test
    void theLastUpgradeIsTheOneToTheCurrentVersion() {
        assertEquals(Version366to400Updater.class, ALL_UPDATERS.get(ALL_UPDATERS.size() - 1));
        assertEquals(List.of(Version366to400Updater.class), UpdateManager.updatersFrom("4.5"));
        assertEquals("4.6", plugin.getConfigVersion(), "a new config version needs a new step in the chain");
    }

    @Test
    void unknownAndUnsupportedVersionsRunNothing() {
        assertTrue(UpdateManager.updatersFrom("3.1").isEmpty(), "3.1 is no longer supported");
        assertTrue(UpdateManager.updatersFrom("9.9").isEmpty());
        assertTrue(UpdateManager.updatersFrom(null).isEmpty());
    }

    // ---- the whole chain on data ---------------------------------------------------------------

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

    @Test
    void aServerOnTheOldestSupportedVersionEndsUpOnTheCurrentOne() throws Exception {
        // What plugin 3.1 (config version 3.2) left behind: old database, old goal file, Translations folder
        plugin.getConfiguration().set("config-version", "3.2");
        plugin.getConfiguration().set("playtime-self-message", "[§6PlayTime§eManager§f]§7 Your playtime is §6%PLAYTIME%");
        plugin.getConfiguration().set("playtime-others-message", "[§6PlayTime§eManager§f]§7 %PLAYER_NAME% has §6%PLAYTIME%");
        sql("DELETE FROM received_rewards", "DELETE FROM rewards_to_be_claimed", "DELETE FROM completed_goals",
                "DROP TABLE play_time",
                "CREATE TABLE play_time (uuid VARCHAR(32) NOT NULL, nickname VARCHAR(32) NOT NULL, playtime BIGINT NOT NULL, " +
                        "artificial_playtime BIGINT NOT NULL, completed_goals TEXT DEFAULT '')",
                "INSERT INTO play_time VALUES ('u1','Alice',100,10,'first,second')",
                "INSERT INTO play_time VALUES ('u1','Alice',50,5,'second')",
                "INSERT INTO play_time VALUES ('u2','Bob',7,0,'')");
        File goal = new File(plugin.getDataFolder(), "Goals/chaingoal.yml");
        goal.getParentFile().mkdirs();
        Files.writeString(goal.toPath(), String.join("\n", "active: false", "time: 5400",
                "goal-message: \"Chain message\"", "commands:", "  - say chain", ""));
        Path translations = new File(plugin.getDataFolder(), "Translations").toPath();
        Files.createDirectories(translations);
        Files.writeString(translations.resolve("legacy.yml"), "custom: translated\n");

        assertTrue(UpdateManager.getInstance().performVersionUpdate("3.2", plugin.getConfigVersion()));

        // Database: duplicates merged, every column added by the later versions, lists turned into rows
        List<String> columns = new ArrayList<>();
        for (List<String> column : rows("PRAGMA table_info(play_time)")) columns.add(column.get(1));
        assertEquals(List.of("uuid", "nickname", "playtime", "artificial_playtime", "afk_playtime", "last_seen",
                "first_join", "relative_join_streak", "absolute_join_streak"), columns);
        assertEquals(List.of(List.of("Alice", "150", "15"), List.of("Bob", "7", "0")),
                rows("SELECT nickname, playtime, artificial_playtime FROM play_time ORDER BY nickname"));
        assertEquals(List.of(List.of("first"), List.of("second")),
                rows("SELECT goal_name FROM completed_goals WHERE nickname = 'Alice' ORDER BY goal_name"),
                "the goals Alice had are in their own table");
        // The journal tables of the latest upgrade exist too
        assertEquals(List.of(), rows("SELECT * FROM playtime_journal"));
        // Files: goal in the newest shape, Customizations instead of Translations, config at the current version
        YamlConfiguration upgradedGoal = YamlConfiguration.loadConfiguration(goal);
        assertEquals(5400, upgradedGoal.getLong("requirements.time"));
        assertEquals(List.of("say chain"), upgradedGoal.getStringList("rewards.commands"));
        assertTrue(upgradedGoal.contains("per-player-check"), "even the last goal upgrade reached this file");
        assertFalse(Files.exists(translations));
        assertTrue(Files.exists(new File(plugin.getDataFolder(), "Customizations/legacy.yml").toPath()));
        assertEquals(plugin.getConfigVersion(), plugin.getConfiguration().getString("config-version", "?"));
    }

    @Test
    void aServerOnAMiddleVersionGetsTheLaterGoalAndRewardChanges() throws Exception {
        // Plugin 3.6.3 (config 4.2): goal file without the per-player option that came with 3.6.6
        File goal = new File(plugin.getDataFolder(), "Goals/middlegoal.yml");
        goal.getParentFile().mkdirs();
        Files.writeString(goal.toPath(), String.join("\n", "active: false", "repeatable: false",
                "check-time-interval: \"900\"", "requirements:", "  time: 1234", "rewards:", "  commands:",
                "    - say middle", ""));
        assertFalse(YamlConfiguration.loadConfiguration(goal).contains("per-player-check"));
        plugin.getConfiguration().set("config-version", "4.2");

        assertTrue(UpdateManager.getInstance().performVersionUpdate("4.2", plugin.getConfigVersion()));

        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(goal);
        assertTrue(upgraded.contains("per-player-check"), "the 3.6.6 goal upgrade ran too");
        assertEquals(1234, upgraded.getLong("requirements.time"));
        assertEquals(plugin.getConfigVersion(), plugin.getConfiguration().getString("config-version", "?"));
    }
}
