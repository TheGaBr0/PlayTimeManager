package me.thegabro.playtimemanager;

import org.junit.jupiter.api.Tag;
import me.thegabro.playtimemanager.Database.DAOs.JoinstreakDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Join streaks: counting joins per cycle, rewards (manual and automatic claim) and the cycle reset. */
@Tag("joinstreak")
class JoinStreakTest extends PluginTestBase {
    private static final String AUTO_CLAIM = "playtime.joinstreak.claim.automatic";
    private static final List<String> EXECUTED = new CopyOnWriteArrayList<>();
    private int nextRewardId = 100;

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

    @BeforeEach
    void noRewardsBetweenTests() {
        RewardRegistry.getInstance().clearRewards();
    }

    /** The cycle reset touches every online player: tests must not see the ones of the previous test. */
    @AfterEach
    void everybodyLeaves() throws Exception {
        for (Player online : List.copyOf(server.getOnlinePlayers())) {
            ((PlayerMock) online).disconnect();
        }
        settle();
    }

    /** A reward far away, so the one under test is not the last of the chain (the last one restarts the cycle). */
    private void aFarAwayReward() {
        reward(50, "far");
    }

    private JoinStreakReward reward(int requiredJoins, String tag) {
        JoinStreakReward reward = new JoinStreakReward(nextRewardId++, requiredJoins);
        reward.addCommand("rewardprobe " + tag + " PLAYER_NAME");
        RewardRegistry.getInstance().addReward(reward);
        // With no rewards at startup the plugin switches the streak check off: turn it on, as an admin would
        plugin.getConfiguration().set("rewards-check-schedule-activation", true);
        return reward;
    }

    private long given(String tag) {
        return EXECUTED.stream().filter(c -> c.startsWith(tag + " ")).count();
    }

    private PlayerMock join(String name, UUID uuid, Boolean autoClaim) throws Exception {
        PlayerMock player = new PlayerMock(server, name, uuid);
        if (autoClaim != null) {
            player.addAttachment(plugin, AUTO_CLAIM, autoClaim);
        }
        server.addPlayer(player);
        settle();
        return player;
    }

    private JoinstreakDAO streaks() {
        return DatabaseHandler.getInstance().getStreakDAO();
    }

    @Test
    void theFirstJoinStartsTheAbsoluteStreak() throws Exception {
        UUID uuid = UUID.randomUUID();
        join("Newcomer", uuid, null);

        assertEquals(1, streaks().getAbsoluteJoinStreak(uuid.toString()));
        assertEquals(0, streaks().getRelativeJoinStreak(uuid.toString()), "no rewards defined: no relative streak");
    }

    @Test
    void joiningAgainInTheSameCycleDoesNotCountTwice() throws Exception {
        UUID uuid = UUID.randomUUID();
        join("Eager", uuid, null).disconnect();
        settle();
        join("Eager", uuid, null).disconnect();
        settle();

        assertEquals(1, streaks().getAbsoluteJoinStreak(uuid.toString()));
    }

    @Test
    void withoutAutoClaimTheRewardWaitsToBeClaimed() throws Exception {
        reward(1, "manual");
        aFarAwayReward();
        UUID uuid = UUID.randomUUID();
        join("Manual", uuid, false);

        List<RewardSubInstance> waiting = streaks().getRewardsToBeClaimed(uuid.toString());
        assertEquals(1, waiting.size(), "queued for /claimrewards");
        assertEquals(1, waiting.get(0).requiredJoins());
        assertEquals(0, given("manual"), "nothing is given before the player claims it");
        assertTrue(streaks().getReceivedRewards(uuid.toString()).isEmpty());
        assertEquals(1, streaks().getRelativeJoinStreak(uuid.toString()));
    }

    @Test
    void withAutoClaimTheRewardIsGivenAtOnce() throws Exception {
        reward(1, "auto");
        aFarAwayReward();
        UUID uuid = UUID.randomUUID();
        join("Automatic", uuid, true);

        assertEquals(1, given("auto"), "the reward command ran");
        assertTrue(EXECUTED.contains("auto Automatic"));
        assertEquals(1, streaks().getReceivedRewards(uuid.toString()).size(), "recorded as received");
        assertTrue(streaks().getRewardsToBeClaimed(uuid.toString()).isEmpty(), "nothing left to claim");
    }

    @Test
    void rewardsAreGivenAcrossCyclesAndTheLastOneRestartsTheStreak() throws Exception {
        reward(1, "day1");
        reward(2, "day2");
        UUID uuid = UUID.randomUUID();
        join("Loyalist", uuid, true);
        assertEquals(1, given("day1"));
        assertEquals(0, given("day2"));

        // The end of a cycle: everybody still online counts as having joined in the new one
        JoinStreaksManager.getInstance().resetMissingPlayerStreaksAsync();
        settle();

        assertEquals(1, given("day1"), "not given twice");
        assertEquals(1, given("day2"), "the second day's reward arrives");
        assertEquals(0, streaks().getRelativeJoinStreak(uuid.toString()), "the last reward restarts the cycle");
        assertEquals(2, streaks().getAbsoluteJoinStreak(uuid.toString()), "the all-time streak keeps counting");
    }

    @Test
    void aMissedCycleResetsTheStreakOfThoseWhoDidNotReturn() throws Exception {
        aFarAwayReward(); // keeps the join streak schedule active
        UUID absent = UUID.randomUUID();
        UUID regular = UUID.randomUUID();
        for (var entry : java.util.Map.of("Absent", absent, "Regular", regular).entrySet()) {
            join(entry.getKey(), entry.getValue(), null).disconnect();
            settle();
            streaks().setRelativeJoinStreak(entry.getValue().toString(), 5);
            streaks().setAbsoluteJoinStreak(entry.getValue().toString(), 9);
        }
        var players = DatabaseHandler.getInstance().getPlayerDAO();
        players.updateLastSeen(absent.toString(), Instant.now().minus(3, ChronoUnit.DAYS));
        players.updateLastSeen(regular.toString(), Instant.now());
        // The reset reads the cached users, which still hold the values from before the changes above
        DBUsersManager.getInstance().removeUserFromCache(absent.toString());
        DBUsersManager.getInstance().removeUserFromCache(regular.toString());

        JoinStreaksManager.getInstance().resetMissingPlayerStreaksAsync();
        Thread.sleep(500);
        settle();

        assertEquals(0, streaks().getRelativeJoinStreak(absent.toString()), "not seen for 3 days: reset");
        assertEquals(5, streaks().getRelativeJoinStreak(regular.toString()), "seen recently: kept");
    }

    @Test
    void claimRewardsOpensTheRewardsGuiForPlayersOnly() throws Exception {
        PlayerMock player = server.addPlayer("Claimer");
        settle();

        server.execute("claimrewards", player);
        settle();
        assertNotNull(player.getOpenInventory().getTopInventory(), "the rewards GUI is open");

        String console = run(server.getConsoleSender(), "claimrewards");
        assertTrue(console.contains("Only players"), console);
    }

    @Test
    void claimRewardsNeedsItsPermission() throws Exception {
        PlayerMock player = server.addPlayer("NoClaim");
        player.addAttachment(plugin, "playtime.joinstreak.claim", false);
        settle();

        server.execute("claimrewards", player);
        settle();

        assertNull(player.getOpenInventory() == null ? null : player.getOpenInventory().getTopInventory(),
                "no GUI without the permission");
    }
}
