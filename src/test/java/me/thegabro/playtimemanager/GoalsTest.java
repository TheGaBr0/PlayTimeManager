package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Goals: files, requirements, rewards, scheduled checks and rewards delivered at login. */
@Tag("goals")
class GoalsTest extends PluginTestBase {
    private static final List<String> EXECUTED = new CopyOnWriteArrayList<>();
    private static final int ONE_MINUTE = 1200; // ticks

    @BeforeAll
    void registerRewardProbe() {
        // The reward commands are dispatched through the server: this one records what it receives
        server.getCommandMap().register("test", new Command("rewardprobe") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                EXECUTED.add(String.join(" ", args));
                return true;
            }
        });
    }

    private Goal goal(String name, long requiredTicks, boolean repeatable) {
        Goal goal = new Goal(plugin, name, false);
        goal.setTime(requiredTicks);
        goal.setRepeatable(repeatable);
        goal.setGoalMessage("Well done %PLAYER_NAME%, goal %GOAL_NAME% reached");
        goal.addCommand("rewardprobe " + name + " PLAYER_NAME");
        GoalsManager.getInstance().addGoal(goal);
        return goal;
    }

    private long rewardsFor(String goalName) {
        return EXECUTED.stream().filter(c -> c.startsWith(goalName + " ")).count();
    }

    private List<String> completedGoals(PlayerMock player) {
        return DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(player.getUniqueId().toString());
    }

    private PlayerMock playerWith(String name, int playedTicks) throws Exception {
        PlayerMock player = server.addPlayer(name);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, playedTicks);
        return player;
    }

    private OnlineUser user(PlayerMock player) {
        return OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
    }

    private String messagesOf(PlayerMock player) {
        StringBuilder all = new StringBuilder();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            all.append(PlainTextComponentSerializer.plainText().serialize(message)).append('\n');
        }
        return all.toString();
    }

    @Test
    void settingsArePersistedInTheGoalFile() {
        Goal goal = goal("persisted", 5000, true);
        goal.addPermission("some.reward.permission");
        goal.addRequirementPermission("some.requirement");
        assertTrue(goal.setCheckTime("120"));
        assertTrue(goal.getGoalFile().exists());

        Goal reloaded = new Goal(plugin, "persisted");
        assertEquals(5000, reloaded.getRequirements().getTime());
        assertTrue(reloaded.isRepeatable());
        assertEquals("120", reloaded.getCompletionCheckInterval());
        assertEquals(120, reloaded.getIntervalSeconds());
        assertTrue(reloaded.getRewardCommands().contains("rewardprobe persisted PLAYER_NAME"));
        assertTrue(reloaded.getRewardPermissions().contains("some.reward.permission"));
        assertTrue(reloaded.getRequirements().getPermissions().contains("some.requirement"));
    }

    @Test
    void anInvalidCheckTimeIsRejected() {
        Goal goal = goal("badtime", 100, false);
        assertFalse(goal.setCheckTime("every now and then"));
        assertFalse(goal.setCheckTime("0"));
    }

    @Test
    void goalsAreLoadedFromTheirFiles() {
        goal("fromfile", 100, false);
        GoalsManager manager = GoalsManager.getInstance();

        manager.clearGoals();
        assertNull(manager.getGoal("fromfile"));
        manager.loadGoals();

        assertNotNull(manager.getGoal("fromfile"));
        assertTrue(manager.getGoalsNames().contains("fromfile"));
    }

    @Test
    void reachingTheRequiredTimeCompletesTheGoalAndGivesTheRewards() throws Exception {
        Goal goal = goal("reach", 2 * ONE_MINUTE, false);
        PlayerMock player = playerWith("Achiever", 3 * ONE_MINUTE);

        goal.checkCompletion(user(player));
        settle();

        assertTrue(completedGoals(player).contains("reach"), "recorded in the database");
        assertEquals(1, rewardsFor("reach"));
        assertTrue(EXECUTED.contains("reach Achiever"), "PLAYER_NAME is replaced in the command");
        assertTrue(messagesOf(player).contains("Well done Achiever, goal reach reached"), "the player is told");
    }

    @Test
    void notEnoughTimeMeansNoGoal() throws Exception {
        Goal goal = goal("toohard", 10 * ONE_MINUTE, false);
        PlayerMock player = playerWith("Hopeful", 2 * ONE_MINUTE);

        goal.checkCompletion(user(player));
        settle();

        assertFalse(completedGoals(player).contains("toohard"));
        assertEquals(0, rewardsFor("toohard"));
    }

    @Test
    void aGoalIsGivenOnlyOnceUnlessRepeatable() throws Exception {
        Goal once = goal("once", ONE_MINUTE, false);
        Goal again = goal("again", ONE_MINUTE, true);
        PlayerMock player = playerWith("Repeater", 5 * ONE_MINUTE);

        for (int i = 0; i < 2; i++) {
            once.checkCompletion(user(player));
            again.checkCompletion(user(player));
            settle();
        }

        assertEquals(1, rewardsFor("once"), "not repeatable: one reward");
        assertEquals(2, rewardsFor("again"), "repeatable: a reward every time");
    }

    @Test
    void permissionRequirementsAreEnforced() throws Exception {
        Goal goal = goal("vip", ONE_MINUTE, false);
        goal.addRequirementPermission("test.vip");
        PlayerMock outsider = playerWith("Outsider", 5 * ONE_MINUTE);
        outsider.addAttachment(plugin, "test.vip", false);
        PlayerMock member = playerWith("Member", 5 * ONE_MINUTE);
        member.addAttachment(plugin, "test.vip", true);

        goal.checkCompletion(user(outsider));
        goal.checkCompletion(user(member));
        settle();

        assertFalse(completedGoals(outsider).contains("vip"));
        assertTrue(completedGoals(member).contains("vip"));
    }

    @Test
    void anActiveGoalIsCheckedOnItsOwnSchedule() throws Exception {
        Goal goal = goal("scheduled", ONE_MINUTE, false);
        assertTrue(goal.setCheckTime("10")); // every 10 seconds
        goal.setActivation(true);
        PlayerMock player = playerWith("Patient", 5 * ONE_MINUTE);

        server.getScheduler().performTicks(150); // 7.5 s: not yet
        settle();
        assertFalse(completedGoals(player).contains("scheduled"), "the first check has not happened yet");

        server.getScheduler().performTicks(100); // past the 10 s mark
        settle();
        assertTrue(completedGoals(player).contains("scheduled"), "completed by the scheduled check");
    }

    @Test
    void anInactiveGoalIsNeverChecked() throws Exception {
        Goal goal = goal("sleeping", ONE_MINUTE, false);
        goal.setCheckTime("10");
        PlayerMock player = playerWith("Ignored", 5 * ONE_MINUTE);

        server.getScheduler().performTicks(1000);
        settle();

        assertFalse(completedGoals(player).contains("sleeping"));
    }

    @Test
    void perPlayerChecksRunForEachPlayerOnTheirOwnTimer() throws Exception {
        Goal goal = goal("perplayer", ONE_MINUTE, false);
        goal.setCheckTime("10");
        goal.setPerPlayerCheck(true);
        goal.setActivation(true);
        PlayerMock player = playerWith("Individual", 5 * ONE_MINUTE); // joins after the goal is active

        server.getScheduler().performTicks(250);
        settle();

        assertTrue(completedGoals(player).contains("perplayer"));
    }

    @Test
    void aGoalCompletedWhileOfflineIsDeliveredAtTheNextLogin() throws Exception {
        goal("whileaway", ONE_MINUTE, false);
        UUID uuid = UUID.randomUUID();
        // The player exists and has a completed-but-not-received goal, as after an offline completion
        PlayerMock first = new PlayerMock(server, "Traveller", uuid);
        server.addPlayer(first);
        settle();
        first.disconnect();
        settle();
        var goals = DatabaseHandler.getInstance().getGoalsDAO();
        goals.addCompletedGoal(uuid.toString(), "Traveller", "whileaway", false);
        assertEquals(List.of("whileaway"), goals.getNotReceivedGoals(uuid.toString()));
        assertEquals(0, rewardsFor("whileaway"));

        PlayerMock back = new PlayerMock(server, "Traveller", uuid);
        server.addPlayer(back);
        settle();

        assertEquals(1, rewardsFor("whileaway"), "the reward is given at login");
        assertTrue(goals.getNotReceivedGoals(uuid.toString()).isEmpty(), "and marked as received");
    }

    @Test
    void renamingAGoalKeepsWhoCompletedIt() throws Exception {
        Goal goal = goal("oldname", ONE_MINUTE, false);
        PlayerMock player = playerWith("Loyal", 5 * ONE_MINUTE);
        goal.checkCompletion(user(player));
        settle();
        assertTrue(completedGoals(player).contains("oldname"));

        goal.rename("newname");

        assertEquals("newname", goal.getName());
        assertTrue(completedGoals(player).contains("newname"), "the completion followed the rename");
        assertFalse(completedGoals(player).contains("oldname"));
        assertEquals(List.of("newname"), completedGoals(player).stream().filter(g -> g.endsWith("name")).collect(Collectors.toList()));
    }

    @Test
    void killingAGoalDeletesItsFile() {
        Goal goal = goal("doomed", 100, false);
        assertTrue(goal.getGoalFile().exists());

        goal.kill(true);

        assertFalse(goal.getGoalFile().exists());
    }
}
