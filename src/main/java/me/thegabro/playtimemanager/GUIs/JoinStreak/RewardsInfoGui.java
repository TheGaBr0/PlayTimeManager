package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.PlayTimeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RewardsInfoGui implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    private static final long INTERACTION_COOLDOWN = 500; // 500ms cooldown between interactions

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public RewardsInfoGui() {}

    public static void openInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, Component.text("§6Claim Your Rewards"));

        // Populate inventory with available rewards
        populateRewards(player, inventory);

        player.openInventory(inventory);
    }

    private static void populateRewards(Player player, Inventory inventory) {
        // TODO: Implement logic to populate rewards based on player's playtime
        // This is a placeholder implementation
        inventory.setItem(22, createRewardItem(Material.DIAMOND, "§bDaily Reward", 1));
    }

    private static ItemStack createRewardItem(Material material, String displayName, int amount) {
        ItemStack item = new ItemStack(material, amount);
        var meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Prevent interactions with non-rewards inventory
        if (!event.getView().getTitle().equals("§6Claim Your Rewards")) return;

        event.setCancelled(true); // Prevent item movement

        // Prevent rapid clicking
        if (isOnCooldown(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

    }

    private void processRewardClaim(Player player, ItemStack rewardItem) {
        // Validate and claim the reward
        if (canClaimReward(player, rewardItem)) {
            grantReward(player, rewardItem);
            updateRewardsData(player, rewardItem);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            player.sendMessage("§cYou cannot claim this reward at the moment.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private boolean canClaimReward(Player player, ItemStack rewardItem) {
        // TODO: Implement specific reward claim validation logic
        // This is a placeholder implementation
        return true;
    }

    private void grantReward(Player player, ItemStack rewardItem) {
        // Add item to player's inventory or handle custom reward logic
        player.getInventory().addItem(rewardItem);
    }

    private void updateRewardsData(Player player, ItemStack rewardItem) {
        // TODO: Update player's rewards data in database/configuration
    }

    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check and update last interaction time
        if (lastInteractionTime.containsKey(playerId)) {
            long lastTime = lastInteractionTime.get(playerId);
            if (currentTime - lastTime < INTERACTION_COOLDOWN) {
                return true;
            }
        }

        lastInteractionTime.put(playerId, currentTime);
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("§6Claim Your Rewards")) {
            // Optional: Perform cleanup or additional actions on inventory close
            UUID playerId = event.getPlayer().getUniqueId();
            lastInteractionTime.remove(playerId);
        }
    }
}