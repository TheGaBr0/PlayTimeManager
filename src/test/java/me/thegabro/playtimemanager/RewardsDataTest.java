package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DAOs.JoinstreakDAO;
import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The data behind join streak rewards: what is stored per player, and what changes for everybody when
 * an admin edits a reward (its join requirement or its removal).
 */
@Tag("joinstreak")
class RewardsDataTest extends PluginTestBase {
    private int nextRewardId = 700;

    @BeforeEach
    void freshRegistry() {
        RewardRegistry.getInstance().clearRewards();
    }

    private JoinstreakDAO dao() {
        return DatabaseHandler.getInstance().getStreakDAO();
    }

    private JoinStreakReward reward(int requiredJoins) {
        JoinStreakReward reward = new JoinStreakReward(nextRewardId++, requiredJoins);
        RewardRegistry.getInstance().addReward(reward);
        return reward;
    }

    private Set<Integer> requiredJoinsReceived(String uuid, int mainInstance) {
        return dao().getReceivedRewards(uuid).stream()
                .filter(r -> r.mainInstanceID() == mainInstance)
                .map(RewardSubInstance::requiredJoins)
                .collect(Collectors.toSet());
    }

    private String playerUuid(String name) throws Exception {
        PlayerMock player = server.addPlayer(name);
        settle();
        return player.getUniqueId().toString();
    }

    // ---- what is stored for a player -----------------------------------------------------------

    @Test
    void receivedAndWaitingRewardsAreStoredPerPlayer() throws Exception {
        String uuid = playerUuid("StoredRewards");
        dao().addReceivedReward(uuid, "StoredRewards", new RewardSubInstance(1, 3, false));
        dao().addRewardToBeClaimed(uuid, "StoredRewards", new RewardSubInstance(2, 5, false));

        assertEquals(List.of(new RewardSubInstance(1, 3, false)), dao().getReceivedRewards(uuid));
        assertEquals(List.of(new RewardSubInstance(2, 5, false)), dao().getRewardsToBeClaimed(uuid));

        dao().removeReceivedReward(uuid, new RewardSubInstance(1, 3, false));
        dao().removeRewardToBeClaimed(uuid, new RewardSubInstance(2, 5, false));
        assertTrue(dao().getReceivedRewards(uuid).isEmpty());
        assertTrue(dao().getRewardsToBeClaimed(uuid).isEmpty());
    }

    @Test
    void waitingRewardsCanBeMarkedAsExpired() throws Exception {
        String uuid = playerUuid("ExpiringRewards");
        dao().addRewardToBeClaimed(uuid, "ExpiringRewards", new RewardSubInstance(1, 1, false));
        dao().addRewardToBeClaimed(uuid, "ExpiringRewards", new RewardSubInstance(1, 2, false));

        dao().markRewardsAsExpired(uuid);

        assertTrue(dao().getRewardsToBeClaimed(uuid).stream().allMatch(RewardSubInstance::expired));
        assertEquals(2, dao().getRewardsToBeClaimed(uuid).size(), "expired, not deleted");
    }

    @Test
    void resettingAPlayerClearsBothLists() throws Exception {
        String uuid = playerUuid("ResetRewards");
        dao().addReceivedReward(uuid, "ResetRewards", new RewardSubInstance(1, 1, false));
        dao().addRewardToBeClaimed(uuid, "ResetRewards", new RewardSubInstance(1, 2, false));

        dao().resetAllUserRewards(uuid);

        assertTrue(dao().getReceivedRewards(uuid).isEmpty());
        assertTrue(dao().getRewardsToBeClaimed(uuid).isEmpty());
    }

    @Test
    void playersWithAStreakAreTheOnesWithAJoinCount() throws Exception {
        String active = playerUuid("StreakActive");
        String idle = playerUuid("StreakIdle");
        dao().setAbsoluteJoinStreak(active, 4);
        dao().setAbsoluteJoinStreak(idle, 0);

        Set<String> withStreak = dao().getPlayersWithActiveStreaks();

        assertTrue(withStreak.contains(active));
        assertFalse(withStreak.contains(idle));
    }

    // ---- editing a reward ----------------------------------------------------------------------

    @Test
    void changingTheRequirementOfASingleJoinRewardUpdatesWhatPlayersAlreadyHave() throws Exception {
        JoinStreakReward reward = reward(3);
        reward.setRepeatable(false);
        String uuid = playerUuid("ChangedRequirement");
        dao().addReceivedReward(uuid, "ChangedRequirement", new RewardSubInstance(reward.getId(), 3, false));
        dao().addRewardToBeClaimed(uuid, "ChangedRequirement", new RewardSubInstance(reward.getId(), 3, false));

        assertTrue(reward.setRequiredJoinsFromString("7"));

        assertEquals(Set.of(7), requiredJoinsReceived(uuid, reward.getId()), "the received entry follows");
        assertEquals(7, dao().getRewardsToBeClaimed(uuid).get(0).requiredJoins(), "so does the waiting one");
        assertArrayEquals(new int[]{7, 7}, reward.getRequiredJoinsRange());
    }

    @Test
    void widenedRangeMarksTheWholeRangeAsReceivedForWhoHadTheReward() throws Exception {
        JoinStreakReward reward = reward(3);
        String had = playerUuid("HadTheReward");
        String never = playerUuid("NeverHadIt");
        dao().addReceivedReward(had, "HadTheReward", new RewardSubInstance(reward.getId(), 3, false));

        assertTrue(reward.setRequiredJoinsFromString("2-5"));

        assertEquals(Set.of(2, 3, 4, 5), requiredJoinsReceived(had, reward.getId()),
                "who already had it is treated as having every join of the new range");
        assertTrue(requiredJoinsReceived(never, reward.getId()).isEmpty(), "the others get nothing");
        assertTrue(reward.isRepeatable(), "a range is always repeatable");
    }

    @Test
    void narrowedRangeDropsTheEntriesThatFallOutsideIt() throws Exception {
        JoinStreakReward reward = reward(1);
        reward.setRequiredJoinsFromString("1-6");
        String uuid = playerUuid("NarrowedRange");
        for (int joins = 1; joins <= 6; joins++) {
            dao().addReceivedReward(uuid, "NarrowedRange", new RewardSubInstance(reward.getId(), joins, false));
        }
        dao().addRewardToBeClaimed(uuid, "NarrowedRange", new RewardSubInstance(reward.getId(), 6, false));

        assertTrue(reward.setRequiredJoinsFromString("2-4"));

        assertEquals(Set.of(2, 3, 4), requiredJoinsReceived(uuid, reward.getId()));
        assertTrue(dao().getRewardsToBeClaimed(uuid).isEmpty(), "a waiting reward outside the range is gone");
    }

    @Test
    void theRequirementIsValidated() {
        JoinStreakReward reward = reward(4);

        for (String invalid : new String[]{"", "0", "-5", "abc", "5-3", "3-", "-3-5", "1-2-3", "2.5", "0-3"}) {
            assertFalse(reward.setRequiredJoinsFromString(invalid), "'" + invalid + "' must be refused");
        }
        assertArrayEquals(new int[]{4, 4}, reward.getRequiredJoinsRange(), "nothing changed");

        assertTrue(reward.setRequiredJoinsFromString("-1"), "-1 disables the reward");
        assertEquals(-1, reward.getMinRequiredJoins());
        assertTrue(reward.setRequiredJoinsFromString(" 9 "));
        assertArrayEquals(new int[]{9, 9}, reward.getRequiredJoinsRange());
    }

    @Test
    void theRequirementIsSavedInTheRewardFile() {
        JoinStreakReward reward = reward(2);
        reward.setRequiredJoinsFromString("3-8");

        JoinStreakReward reloaded = new JoinStreakReward(reward.getId(), 99);

        assertArrayEquals(new int[]{3, 8}, reloaded.getRequiredJoinsRange());
        assertTrue(reloaded.isRepeatable());
    }

    @Test
    void changingARewardUpdatesThePlayersAlreadyLoadedInMemory() throws Exception {
        JoinStreakReward reward = reward(3);
        reward.setRepeatable(false);
        PlayerMock player = server.addPlayer("InMemory");
        settle();
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(player.getUniqueId().toString());
        user.addReceivedReward(new RewardSubInstance(reward.getId(), 3, false));
        settle();

        reward.setRequiredJoinsFromString("8");

        assertEquals(List.of(new RewardSubInstance(reward.getId(), 8, false)), user.getReceivedRewards(),
                "no need to wait for a reload");
    }

    @Test
    void removingARewardRemovesItFromEveryPlayer() throws Exception {
        JoinStreakReward doomed = reward(2);
        JoinStreakReward kept = reward(5);
        PlayerMock online = server.addPlayer("OnlineHolder");
        settle();
        String onlineUuid = online.getUniqueId().toString();
        String offlineUuid = playerUuid("OfflineHolder");
        var user = OnlineUsersManager.getInstance().getOnlineUserByUUID(onlineUuid);
        for (String uuid : new String[]{onlineUuid, offlineUuid}) {
            dao().addReceivedReward(uuid, "x", new RewardSubInstance(doomed.getId(), 2, false));
            dao().addRewardToBeClaimed(uuid, "x", new RewardSubInstance(doomed.getId(), 2, false));
            dao().addReceivedReward(uuid, "x", new RewardSubInstance(kept.getId(), 5, false));
        }
        user.addReceivedReward(new RewardSubInstance(doomed.getId(), 2, false));
        settle();

        doomed.kill(false);
        settle();

        assertNull(RewardRegistry.getInstance().getReward(doomed.getId()), "gone from the registry");
        assertFalse(doomed.getRewardFile().exists(), "its file is deleted");
        for (String uuid : new String[]{onlineUuid, offlineUuid}) {
            assertTrue(requiredJoinsReceived(uuid, doomed.getId()).isEmpty());
            assertTrue(dao().getRewardsToBeClaimed(uuid).stream().noneMatch(r -> r.mainInstanceID() == doomed.getId()));
            assertEquals(Set.of(5), requiredJoinsReceived(uuid, kept.getId()), "other rewards are untouched");
        }
        assertTrue(user.getReceivedRewards().stream().noneMatch(r -> r.mainInstanceID() == doomed.getId()),
                "also from the player already in memory");
    }

    @Test
    void killingForAnUpdateKeepsThePlayersData() throws Exception {
        JoinStreakReward reward = reward(2);
        String uuid = playerUuid("KeepsData");
        dao().addReceivedReward(uuid, "KeepsData", new RewardSubInstance(reward.getId(), 2, false));

        reward.kill(true);

        assertEquals(Set.of(2), requiredJoinsReceived(uuid, reward.getId()), "update=true only drops the reward itself");
    }
}
