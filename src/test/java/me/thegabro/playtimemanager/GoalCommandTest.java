package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import org.bukkit.Statistic;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** /playtimegoal: create, rename, remove, check now and the GUI. */
@Tag("goals")
@Tag("commands")
class GoalCommandTest extends PluginTestBase {

    private ConsoleCommandSenderMock console() {
        return server.getConsoleSender();
    }

    private GoalsManager goals() {
        return GoalsManager.getInstance();
    }

    @Test
    void aCreatedGoalIsRegisteredAndSavedAsInactive() throws Exception {
        String created = run(console(), "playtimegoal", "create", "cmdgoal");
        assertTrue(created.contains("cmdgoal") && created.contains("created"), created);
        assertNotNull(goals().getGoal("cmdgoal"), "registered, so it can be used right away");
        assertTrue(goals().getGoal("cmdgoal").getGoalFile().exists(), "saved as a file");
        assertFalse(goals().getGoal("cmdgoal").isActive(), "inactive by default");

        String duplicate = run(console(), "playtimegoal", "create", "cmdgoal");
        assertTrue(duplicate.contains("already exists"), duplicate);
    }

    @Test
    void aGoalCanBeRemoved() throws Exception {
        run(console(), "playtimegoal", "create", "shortlived");
        java.io.File file = goals().getGoal("shortlived").getGoalFile();

        String removed = run(console(), "playtimegoal", "remove", "shortlived");

        assertTrue(removed.contains("has been removed"), removed);
        assertNull(goals().getGoal("shortlived"));
        assertFalse(file.exists(), "the file is deleted");
    }

    @Test
    void aGoalCanBeRenamed() throws Exception {
        run(console(), "playtimegoal", "create", "beforename");

        String renamed = run(console(), "playtimegoal", "rename", "beforename", "aftername");

        assertTrue(renamed.contains("renamed"), renamed);
        assertNull(goals().getGoal("beforename"));
        assertNotNull(goals().getGoal("aftername"));
        java.io.File folder = goals().getGoal("aftername").getGoalFile().getParentFile();
        assertTrue(new java.io.File(folder, "aftername.yml").exists(), "the file moved to the new name");
        assertFalse(new java.io.File(folder, "beforename.yml").exists());

        // Later edits are saved to the new file: the old one must not come back
        goals().getGoal("aftername").setRepeatable(true);
        assertFalse(new java.io.File(folder, "beforename.yml").exists(), "the old file is not recreated");
        assertTrue(org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new java.io.File(folder, "aftername.yml")).getBoolean("repeatable"));
    }

    /** The manager keeps goals in a HashSet hashed by name, so a rename must not leave a stale entry. */
    @Test
    void aRenamedGoalCanBeRemoved() throws Exception {
        run(console(), "playtimegoal", "create", "renamethenremove");
        run(console(), "playtimegoal", "rename", "renamethenremove", "renamedthenremoved");
        java.io.File file = new java.io.File(goals().getGoal("renamedthenremoved").getGoalFile().getParentFile(),
                "renamedthenremoved.yml");

        run(console(), "playtimegoal", "remove", "renamedthenremoved");

        assertNull(goals().getGoal("renamedthenremoved"));
        assertFalse(file.exists());
    }

    @Test
    void badGoalCommandsAreRefused() throws Exception {
        assertTrue(run(console(), "playtimegoal", "remove", "ghostgoal").contains("doesn't exist"));
        assertTrue(run(console(), "playtimegoal", "rename", "ghostgoal", "other").contains("doesn't exist"));
        assertTrue(run(console(), "playtimegoal", "checknow", "ghostgoal").contains("doesn't exist"));
        assertTrue(run(console(), "playtimegoal", "create").contains("Usage"));
        assertTrue(run(console(), "playtimegoal", "dance").contains("not valid"));

        run(console(), "playtimegoal", "create", "taken");
        run(console(), "playtimegoal", "create", "taken2");
        assertTrue(run(console(), "playtimegoal", "rename", "taken", "taken2").contains("already exists"));
    }

    @Test
    void checkNowCompletesTheGoalsOfTheOnlinePlayersWhoQualify() throws Exception {
        run(console(), "playtimegoal", "create", "nowgoal");
        Goal goal = goals().getGoal("nowgoal");
        goal.setTime(1200);
        PlayerMock qualified = server.addPlayer("Qualified");
        PlayerMock short1 = server.addPlayer("TooShort");
        settle();
        qualified.setStatistic(Statistic.PLAY_ONE_MINUTE, 2400);
        short1.setStatistic(Statistic.PLAY_ONE_MINUTE, 100);

        run(console(), "playtimegoal", "checknow", "nowgoal");
        settle();

        var done = DatabaseHandler.getInstance().getGoalsDAO();
        assertTrue(done.getCompletedGoals(qualified.getUniqueId().toString()).contains("nowgoal"));
        assertFalse(done.getCompletedGoals(short1.getUniqueId().toString()).contains("nowgoal"));
    }

    @Test
    void theConsoleCannotOpenTheGui_aPlayerCan() throws Exception {
        assertTrue(run(console(), "playtimegoal").contains("Only players"));

        PlayerMock admin = server.addPlayer("GoalAdmin");
        admin.setOp(true); // playtime.goal is not granted to normal players
        settle();
        server.execute("playtimegoal", admin);
        settle();
        assertNotNull(admin.getOpenInventory().getTopInventory(), "the goals GUI is open");
    }

    @Test
    void thePermissionIsRequired() throws Exception {
        PlayerMock nobody = server.addPlayer("GoalNobody");
        nobody.addAttachment(plugin, "playtime.goal", false);
        settle();

        String out = run(nobody, "playtimegoal", "create", "forbidden");

        assertTrue(out.contains("permission"), out);
        assertNull(goals().getGoal("forbidden"));
    }
}
