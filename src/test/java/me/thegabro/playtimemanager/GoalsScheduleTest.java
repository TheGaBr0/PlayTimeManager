package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Goals checked on a cron schedule, per player or for players who are offline. The cron used fires
 * every 10 seconds, so the simulated server only has to advance a few hundred ticks.
 */
@Tag("goals")
class GoalsScheduleTest extends PluginTestBase {
    private static final String EVERY_10_SECONDS = "0/10 * * * * ?";
    private static final int ONE_MINUTE = 1200; // ticks
    private static final List<String> EXECUTED = new CopyOnWriteArrayList<>();

    @BeforeAll
    void registerRewardProbe() {
        server.getCommandMap().register("test", new Command("rewardprobe") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                EXECUTED.add(String.join(" ", args));
                return true;
            }
        });
    }

    /** Goals keep checking on their schedule: stop them all so a test does not see the previous one's. */
    @AfterEach
    void stopGoals() throws Exception {
        GoalsManager.getInstance().clearGoals();
        for (Player online : List.copyOf(server.getOnlinePlayers())) {
            ((PlayerMock) online).disconnect();
        }
        settle();
    }

    private Goal goal(String name, boolean repeatable) {
        Goal goal = new Goal(plugin, name, false);
        goal.setTime(ONE_MINUTE);
        goal.setRepeatable(repeatable);
        goal.addCommand("rewardprobe " + name + " PLAYER_NAME");
        GoalsManager.getInstance().addGoal(goal);
        return goal;
    }

    private PlayerMock playerWithTime(String name) throws Exception {
        PlayerMock player = server.addPlayer(name);
        settle();
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 5 * ONE_MINUTE);
        return player;
    }

    private long rewards(String goalName) {
        return EXECUTED.stream().filter(c -> c.startsWith(goalName + " ")).count();
    }

    private List<String> completed(PlayerMock player) {
        return DatabaseHandler.getInstance().getGoalsDAO().getCompletedGoals(player.getUniqueId().toString());
    }

    @Test
    void aCronGoalIsCheckedWhenItsScheduleFires() throws Exception {
        Goal goal = goal("cron_basic", false);
        assertTrue(goal.setCheckTime(EVERY_10_SECONDS));
        goal.setActivation(true);
        assertTrue(goal.isUsingCronExpression());
        PlayerMock player = playerWithTime("CronPlayer");

        server.getScheduler().performTicks(260);
        settle();

        assertTrue(completed(player).contains("cron_basic"));
        assertEquals(1, rewards("cron_basic"));
    }

    @Test
    void theCronIsRescheduledAfterEveryCheck() throws Exception {
        Goal goal = goal("cron_repeat", true);
        goal.setCheckTime(EVERY_10_SECONDS);
        goal.setActivation(true);
        PlayerMock player = playerWithTime("CronRepeater");

        server.getScheduler().performTicks(260);
        settle();
        long afterFirstWindow = rewards("cron_repeat");
        // (The simulated clock does not move the real one, so the cron may fire again within the window)
        assertTrue(afterFirstWindow >= 1, "the first check happened");

        server.getScheduler().performTicks(260);
        settle();
        assertTrue(rewards("cron_repeat") > afterFirstWindow,
                "the schedule was armed again, and a repeatable goal pays again");
        assertTrue(completed(player).contains("cron_repeat"));
    }

    @Test
    void aNonRepeatableCronGoalPaysOnlyOnce() throws Exception {
        Goal goal = goal("cron_once", false);
        goal.setCheckTime(EVERY_10_SECONDS);
        goal.setActivation(true);
        playerWithTime("CronOnce");

        for (int i = 0; i < 3; i++) {
            server.getScheduler().performTicks(260);
            settle();
        }

        assertEquals(1, rewards("cron_once"));
    }

    @Test
    void perPlayerCheckWorksWithCronToo() throws Exception {
        Goal goal = goal("cron_each", false);
        goal.setCheckTime(EVERY_10_SECONDS);
        goal.setPerPlayerCheck(true);
        goal.setActivation(true);
        PlayerMock player = playerWithTime("CronEach"); // joins after the goal is active

        server.getScheduler().performTicks(260);
        settle();

        assertTrue(completed(player).contains("cron_each"));
    }

    @Test
    void thePlayerCheckStopsWhenThePlayerLeaves() throws Exception {
        Goal goal = goal("cron_leaves", true);
        goal.setCheckTime(EVERY_10_SECONDS);
        goal.setPerPlayerCheck(true);
        goal.setActivation(true);
        PlayerMock player = playerWithTime("CronLeaver");
        player.disconnect();
        settle();

        server.getScheduler().performTicks(500);
        settle();

        assertEquals(0, rewards("cron_leaves"), "nobody is online, so nobody is checked");
    }

    @Test
    void invalidCronExpressionsAreRefusedAndTheOldScheduleStays() {
        Goal goal = goal("cron_invalid", false);
        assertTrue(goal.setCheckTime("120"));

        for (String invalid : new String[]{"not a cron", "61 * * * * ?", "* * *", "0 0 0 32 * ?", "-5", "0"}) {
            assertFalse(goal.setCheckTime(invalid), "'" + invalid + "' must be refused");
        }

        assertEquals("120", goal.getCompletionCheckInterval(), "the previous setting is kept");
        assertFalse(goal.isUsingCronExpression());
    }

    @Test
    void anEmptyCheckTimeFallsBackToTheFifteenMinuteDefault() {
        Goal goal = goal("cron_empty", false);
        goal.setCheckTime("120");

        assertTrue(goal.setCheckTime(""), "an empty value is accepted...");

        assertEquals(900, goal.getIntervalSeconds(), "...and means the default of 900 seconds");
        assertFalse(goal.isUsingCronExpression());
    }

    @Test
    void theNextCheckIsReported() {
        Goal goal = goal("cron_next", false);
        goal.setCheckTime(EVERY_10_SECONDS);
        goal.setActivation(true);

        Map<String, Object> schedule = goal.getNextSchedule();

        assertInstanceOf(Date.class, schedule.get("nextCheck"));
        long millisToNext = ((Date) schedule.get("nextCheck")).getTime() - System.currentTimeMillis();
        assertTrue(millisToNext > -1000 && millisToNext <= 11_000, "within the next 10 seconds: " + millisToNext);
        assertNotNull(schedule.get("timeCheckToText"));

        goal.setActivation(false);
        assertEquals("-", goal.getNextSchedule().get("nextCheck"), "nothing is scheduled for an inactive goal");
    }

    @Test
    void changingTheCheckTimeReplacesTheRunningSchedule() throws Exception {
        Goal goal = goal("cron_change", true);
        goal.setCheckTime("3600"); // once an hour: would never fire in this test
        goal.setActivation(true);
        PlayerMock player = playerWithTime("CronChanger");

        server.getScheduler().performTicks(260);
        settle();
        assertEquals(0, rewards("cron_change"), "the hourly schedule has not fired");

        assertTrue(goal.setCheckTime(EVERY_10_SECONDS));
        server.getScheduler().performTicks(260);
        settle();

        assertTrue(rewards("cron_change") >= 1, "the new schedule is the one running now");
        assertTrue(completed(player).contains("cron_change"));
    }

    @Test
    void offlinePlayersWhoRecentlyPlayedAreCheckedWhenOfflineRewardsAreOn() throws Exception {
        Goal goal = goal("offline_rewards", false);
        goal.setOfflineRewardEnabling(true);
        goal.setCheckTime("10");
        goal.setActivation(true);
        PlayerMock player = playerWithTime("OfflineEarner");
        player.disconnect(); // seen "just now": inside the 10 seconds window
        settle();

        server.getScheduler().performTicks(260);
        settle();
        Thread.sleep(300);
        drain();

        var goals = DatabaseHandler.getInstance().getGoalsDAO();
        String uuid = player.getUniqueId().toString();
        assertTrue(goals.getCompletedGoals(uuid).contains("offline_rewards"), "completed while offline");
        assertTrue(goals.getNotReceivedGoals(uuid).contains("offline_rewards"), "and waiting to be delivered at login");
        assertEquals(0, rewards("offline_rewards"), "nothing is given to a player who is not there");
    }

    @Test
    void offlinePlayersAreIgnoredWhenOfflineRewardsAreOff() throws Exception {
        Goal goal = goal("no_offline", false);
        goal.setCheckTime("10");
        goal.setActivation(true);
        PlayerMock player = playerWithTime("OfflineIgnored");
        player.disconnect();
        settle();

        server.getScheduler().performTicks(260);
        settle();
        Thread.sleep(300);
        drain();

        assertFalse(DatabaseHandler.getInstance().getGoalsDAO()
                .getCompletedGoals(player.getUniqueId().toString()).contains("no_offline"));
    }
}
