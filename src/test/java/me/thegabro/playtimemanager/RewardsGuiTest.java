package me.thegabro.playtimemanager;

import me.thegabro.playtimemanager.Database.DatabaseHandler;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** /claimrewards: what the rewards GUI shows and what clicking in it does. */
@Tag("joinstreak")
class RewardsGuiTest extends PluginTestBase {
    private static final List<String> EXECUTED = new CopyOnWriteArrayList<>();
    private static final int CLAIM_ALL_SLOT = 46;
    private static final int FILTER_LOCKED_SLOT = 5;
    private int nextRewardId = 500;

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
        plugin.getConfiguration().set("rewards-check-schedule-activation", true);
    }

    @AfterEach
    void everybodyLeaves() throws Exception {
        for (Player online : List.copyOf(server.getOnlinePlayers())) {
            ((PlayerMock) online).disconnect();
        }
        settle();
    }

    private JoinStreakReward reward(int requiredJoins, String tag) {
        JoinStreakReward reward = new JoinStreakReward(nextRewardId++, requiredJoins);
        reward.addCommand("rewardprobe " + tag + " PLAYER_NAME");
        RewardRegistry.getInstance().addReward(reward);
        return reward;
    }

    private long given(String tag) {
        return EXECUTED.stream().filter(c -> c.startsWith(tag + " ")).count();
    }

    /** A player who joined without the automatic claim permission, so the reward waits in the GUI. */
    private PlayerMock joinWithoutAutoClaim(String name) throws Exception {
        PlayerMock player = new PlayerMock(server, name, UUID.randomUUID());
        player.addAttachment(plugin, "playtime.joinstreak.claim.automatic", false);
        server.addPlayer(player);
        settle();
        return player;
    }

    private Inventory openRewardsGui(PlayerMock player) throws Exception {
        server.execute("claimrewards", player);
        settle();
        return player.getOpenInventory().getTopInventory();
    }

    /** The "reward_type" the plugin stamps on a reward item (CLAIMABLE, CLAIMED, LOCKED), or null. */
    private String rewardType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "reward_type"), PersistentDataType.STRING);
    }

    private List<Integer> slotsOfType(Inventory gui, String type) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < gui.getSize(); slot++) {
            if (type.equals(rewardType(gui.getItem(slot)))) slots.add(slot);
        }
        return slots;
    }

    @Test
    void anAvailableRewardCanBeClaimedWithAClick() throws Exception {
        reward(1, "clickme");
        reward(50, "far");
        PlayerMock player = joinWithoutAutoClaim("Clicker");
        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        assertEquals(1, streaks.getRewardsToBeClaimed(player.getUniqueId().toString()).size(), "setup: waiting");

        Inventory gui = openRewardsGui(player);
        List<Integer> claimable = slotsOfType(gui, "CLAIMABLE");
        assertEquals(1, claimable.size(), "the GUI offers the reward");

        player.simulateInventoryClick(claimable.get(0));
        settle();

        assertEquals(1, given("clickme"), "the reward command ran");
        assertTrue(EXECUTED.contains("clickme Clicker"));
        assertTrue(streaks.getRewardsToBeClaimed(player.getUniqueId().toString()).isEmpty(), "no longer waiting");
        assertEquals(1, streaks.getReceivedRewards(player.getUniqueId().toString()).size(), "recorded as received");
        assertTrue(slotsOfType(player.getOpenInventory().getTopInventory(), "CLAIMABLE").isEmpty(),
                "and the GUI no longer offers it");
    }

    @Test
    void claimAllClaimsEveryWaitingReward() throws Exception {
        reward(1, "allA");
        reward(2, "allB");
        reward(50, "far");
        PlayerMock player = joinWithoutAutoClaim("AllClaimer");
        // The end of the cycle: the second day's reward becomes available too
        JoinStreaksManager.getInstance().resetMissingPlayerStreaksAsync();
        settle();
        var streaks = DatabaseHandler.getInstance().getStreakDAO();
        assertEquals(2, streaks.getRewardsToBeClaimed(player.getUniqueId().toString()).size(), "setup: two waiting");

        openRewardsGui(player);
        player.simulateInventoryClick(CLAIM_ALL_SLOT);
        settle();

        assertEquals(1, given("allA"));
        assertEquals(1, given("allB"));
        assertTrue(streaks.getRewardsToBeClaimed(player.getUniqueId().toString()).isEmpty());
        assertEquals(2, streaks.getReceivedRewards(player.getUniqueId().toString()).size());
    }

    @Test
    void whoOnlyLooksAtAnotherPlayersRewardsCannotClaimThem() throws Exception {
        reward(1, "stolen");
        reward(50, "far");
        PlayerMock owner = joinWithoutAutoClaim("RewardOwner");
        PlayerMock watcher = server.addPlayer("RewardWatcher");
        watcher.setOp(true); // seeplayer needs playtime.joinstreak.seeplayer
        settle();

        server.execute("playtimejoinstreak", watcher, "seeplayer", "RewardOwner");
        settle();
        Inventory gui = watcher.getOpenInventory().getTopInventory();
        assertNotNull(gui, "the watcher sees the owner's rewards");
        List<Integer> claimable = slotsOfType(gui, "CLAIMABLE");
        assertEquals(1, claimable.size());

        watcher.simulateInventoryClick(claimable.get(0));
        watcher.simulateInventoryClick(CLAIM_ALL_SLOT);
        settle();

        // (the watcher is an operator, so they get their own reward at login: only the owner's matters)
        assertFalse(EXECUTED.contains("stolen RewardOwner"), "the owner's reward was not given away");
        assertEquals(1, DatabaseHandler.getInstance().getStreakDAO()
                .getRewardsToBeClaimed(owner.getUniqueId().toString()).size(), "still waiting for the owner");
    }

    @Test
    void theFilterButtonsSwitchBetweenAvailableAndLockedRewards() throws Exception {
        reward(1, "avail");
        reward(50, "locked");
        PlayerMock player = joinWithoutAutoClaim("Filterer");

        Inventory gui = openRewardsGui(player);
        assertEquals(1, slotsOfType(gui, "CLAIMABLE").size(), "available is shown by default");
        assertTrue(slotsOfType(gui, "LOCKED").isEmpty());

        player.simulateInventoryClick(FILTER_LOCKED_SLOT);
        settle();
        Inventory locked = player.getOpenInventory().getTopInventory();
        assertTrue(slotsOfType(locked, "CLAIMABLE").isEmpty(), "the locked filter hides the claimable one");
        assertFalse(slotsOfType(locked, "LOCKED").isEmpty(), "and shows the rewards that are still locked");
    }

    @Test
    void closingTheGuiEndsItsSession() throws Exception {
        reward(1, "session");
        reward(50, "far");
        PlayerMock player = joinWithoutAutoClaim("Sessional2");
        Inventory gui = openRewardsGui(player);
        assertNotNull(gui);

        player.closeInventory();
        settle();

        assertFalse(me.thegabro.playtimemanager.GUIs.InventoryListener.getInstance()
                .hasActiveGUI(player.getUniqueId()), "the GUI is no longer registered for the player");
    }
}
