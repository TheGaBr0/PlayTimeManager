package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Goals and join streak rewards listed by /playtimejournal. */
@Tag("journal")
class JournalEventsTest extends PluginTestBase {
    private final String yesterday = LocalDate.now().minusDays(1).toString();
    private final String tomorrow = LocalDate.now().plusDays(1).toString();

    @Test
    void goalsAndRewardsOfThePeriodAreListed() throws Exception {
        PlayerMock player = server.addPlayer("Eventful");
        settle();
        String uuid = player.getUniqueId().toString();
        var db = DatabaseHandler.getInstance();
        db.getGoalsDAO().addCompletedGoal(uuid, "Eventful", "welcome_goal", false);
        db.getStreakDAO().addReceivedReward(uuid, "Eventful", new RewardSubInstance(7, 3, false));

        String out = run(server.getConsoleSender(), "playtimejournal", "Eventful", yesterday, tomorrow);

        assertTrue(out.contains("Goals completed: 1"), out);
        assertTrue(out.contains("welcome_goal"), out);
        assertTrue(out.contains("Join streak rewards received: 1"), out);
        assertTrue(out.contains("Reward #7 (3 joins)"), out);
    }

    @Test
    void eventsOutsideThePeriodAreNotListed() throws Exception {
        PlayerMock player = server.addPlayer("Past");
        settle();
        String uuid = player.getUniqueId().toString();
        var db = DatabaseHandler.getInstance();
        db.getGoalsDAO().addCompletedGoal(uuid, "Past", "recent_goal", false);
        db.getStreakDAO().addReceivedReward(uuid, "Past", new RewardSubInstance(1, 1, false));

        String out = run(server.getConsoleSender(), "playtimejournal", "Past", "2020-01-01", "2020-12-31");

        assertTrue(out.contains("Goals completed: 0"), out);
        assertTrue(out.contains("Join streak rewards received: 0"), out);
        assertFalse(out.contains("recent_goal"), out);
    }

    @Test
    void aLongListIsShortened() throws Exception {
        PlayerMock player = server.addPlayer("Collector");
        settle();
        String uuid = player.getUniqueId().toString();
        for (int i = 1; i <= 12; i++) {
            DatabaseHandler.getInstance().getGoalsDAO().addCompletedGoal(uuid, "Collector", "goal_" + i, false);
        }

        String out = run(server.getConsoleSender(), "playtimejournal", "Collector", yesterday, tomorrow);

        assertTrue(out.contains("Goals completed: 12"), out);
        assertTrue(out.contains("and 2 more"), out);
    }
}
