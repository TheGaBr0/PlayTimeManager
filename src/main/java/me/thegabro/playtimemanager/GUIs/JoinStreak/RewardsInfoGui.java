package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RewardsInfoGui implements InventoryHolder, Listener {

    private Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final JoinStreaksManager rewardsManager = JoinStreaksManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private Player player;

    // Pagination variables
    private int currentPage = 0;
    private final List<RewardDisplayItem> displayItems = new ArrayList<>();
    private final int REWARDS_PER_PAGE = 28; // Maximum number of rewards per page
    private final int NEXT_BUTTON_SLOT = 50;
    private final int PREV_BUTTON_SLOT = 48;
    private final int PAGE_INDICATOR_SLOT = 49;

    // Interaction cooldown
    private static final Map<UUID, Long> lastInteractionTime = new HashMap<>();
    private static final long INTERACTION_COOLDOWN = 500; // 500ms cooldown between interactions

    public RewardsInfoGui(){}

    public RewardsInfoGui(Player player) {
        this.player = player;
        inv = Bukkit.createInventory(this, 54, Component.text("§6Claim Your Rewards"));
    }

    public void openInventory() {
        currentPage = 0; // Reset to first page when opening the GUI
        initializeItems();
        player.openInventory(inv);
    }

    public void openInventory(int page) {
        currentPage = page;
        initializeItems();
        player.openInventory(inv);
    }

    private static class RewardDisplayItem {
        private final JoinStreakReward reward;
        private final float instance;
        private final RewardStatus status;

        public RewardDisplayItem(JoinStreakReward reward, float instance, RewardStatus status) {
            this.reward = reward;
            this.instance = instance;
            this.status = status;
        }

        public JoinStreakReward getReward() {
            return reward;
        }

        public float getInstance() {
            return instance;
        }

        public RewardStatus getStatus() {
            return status;
        }
    }

    private enum RewardStatus {
        CLAIMED,
        AVAILABLE,
        LOCKED
    }

    public void initializeItems() {
        int leftIndex = 9;
        int rightIndex = 17;

        // Process all rewards and their instances to display items
        displayItems.clear();
        Map<Integer, LinkedHashSet<Float>> joinRewardsMap = rewardsManager.getJoinRewardsMap();
        Set<Float> rewardsReceived = dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getReceivedRewards();
        Set<Float> rewardsToBeClaimed = dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRewardsToBeClaimed();

        // For each reward ID and its instances
        for (Map.Entry<Integer, LinkedHashSet<Float>> entry : joinRewardsMap.entrySet()) {
            int rewardId = entry.getKey();
            JoinStreakReward baseReward = rewardsManager.getReward(rewardId);

            if (baseReward == null) continue;

            // Get the instances sorted
            List<Float> sortedInstances = new ArrayList<>(entry.getValue());
            Collections.sort(sortedInstances);

            // Calculate how many discrete instances we need to display
            int minJoins = baseReward.getMinRequiredJoins();
            int maxJoins = baseReward.getMaxRequiredJoins();

            // Skip rewards with invalid ranges (e.g., -1 which might be used for special cases)
            if (minJoins <= 0 && minJoins != -1) continue;

            // For each instance of this reward
            for (Float instance : sortedInstances) {

                RewardStatus status;
                if (rewardsReceived.contains(instance)) {
                    status = RewardStatus.CLAIMED;
                } else if (rewardsToBeClaimed.contains(instance)) {
                    status = RewardStatus.AVAILABLE;
                } else {
                    status = RewardStatus.LOCKED;
                }

                displayItems.add(new RewardDisplayItem(baseReward, instance, status));
            }
        }

        // Sort displayItems: AVAILABLE first, then LOCKED, then CLAIMED
        displayItems.sort((a, b) -> {
            if (a.getStatus() != b.getStatus()) {
                return a.getStatus().ordinal() - b.getStatus().ordinal();
            }

            // If same status, sort by required joins
            int aJoins = a.getReward().getMinRequiredJoins();
            int bJoins = b.getReward().getMinRequiredJoins();

            if (aJoins != bJoins) {
                return Integer.compare(aJoins, bJoins);
            }

            // If same required joins, sort by instance
            return Float.compare(a.getInstance(), b.getInstance());
        });

        protectedSlots.clear();
        inv.clear();

        // Create GUI borders
        for(int i = 0; i < 54; i++) {
            if(i <= 9 || i >= 45 || i == leftIndex || i == rightIndex) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Component.text("§f[§6P.T.M.§f]§7")));
                protectedSlots.add(i);
                if(i == leftIndex) leftIndex += 9;
                if(i == rightIndex) rightIndex += 9;
            }
        }

        // Add pagination controls if needed
        int totalPages = (int) Math.ceil((double) displayItems.size() / REWARDS_PER_PAGE);

        if (totalPages > 1) {
            // Page indicator
            inv.setItem(PAGE_INDICATOR_SLOT, createGuiItem(
                    Material.PAPER,
                    Component.text("§e§lPage " + (currentPage + 1) + " of " + totalPages)
            ));
            protectedSlots.add(PAGE_INDICATOR_SLOT);

            // Next page button
            if (currentPage < totalPages - 1) {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(
                        Material.ARROW,
                        Component.text("§a§lNext Page →"),
                        Component.text("§7Click to view the next page")
                ));
            } else {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(
                        Material.BARRIER,
                        Component.text("§c§lNo More Pages")
                ));
            }
            protectedSlots.add(NEXT_BUTTON_SLOT);

            // Previous page button
            if (currentPage > 0) {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(
                        Material.ARROW,
                        Component.text("§a§l← Previous Page"),
                        Component.text("§7Click to view the previous page")
                ));
            } else {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(
                        Material.BARRIER,
                        Component.text("§c§lFirst Page")
                ));
            }
            protectedSlots.add(PREV_BUTTON_SLOT);
        }

        if(!displayItems.isEmpty()) {
            // Calculate start and end indices for current page
            int startIndex = currentPage * REWARDS_PER_PAGE;
            int endIndex = Math.min(startIndex + REWARDS_PER_PAGE, displayItems.size());

            // Get subset of rewards for current page
            List<RewardDisplayItem> currentPageRewards = displayItems.subList(startIndex, endIndex);

            int slot = 10; // Start at first available slot after top border
            for(RewardDisplayItem displayItem : currentPageRewards) {
                // Find next available slot
                while(protectedSlots.contains(slot)) slot++;
                if(slot >= 45) break; // Stop before bottom border

                // Create reward item based on status
                JoinStreakReward reward = displayItem.getReward();
                float instance = displayItem.getInstance();

                Material material;
                String statusPrefix;
                List<Component> lore = new ArrayList<>();
                int specificJoinCount;
                switch (displayItem.getStatus()) {
                    case AVAILABLE:
                        material = Material.valueOf(reward.getItemIcon());
                        statusPrefix = "§a§l[CLICK TO CLAIM] ";
                        lore.add(Component.text("§aThis reward is available to claim!"));
                        lore.add(Component.text("§7Click to receive your reward"));
                        break;
                    case CLAIMED:
                        material = Material.valueOf(reward.getItemIcon());;
                        statusPrefix = "§8§l[CLAIMED] ";
                        lore.add(Component.text("§8You've already claimed this reward"));
                        break;
                    case LOCKED:
                    default:
                        material = Material.valueOf(reward.getItemIcon());;
                        statusPrefix = "§c§l[LOCKED] ";
                        lore.add(Component.text("§cYou haven't reached this join streak yet"));

                        break;
                }

                // Add reward details to lore
                specificJoinCount = calculateSpecificJoinCount(reward, instance);
                lore.add(Component.text("§7Required Joins: §e" +
                        (specificJoinCount == -1 ? "-" : specificJoinCount)));
                lore.add(Component.text("§7Reward ID: §e" + instance));

                if(!reward.getDescription().isEmpty()) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("§7" + reward.getDescription()));
                }
                if(!reward.getRewardDescription().isEmpty()) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("§7Reward: " + reward.getRewardDescription()));
                }

                if (!reward.getPermissions().isEmpty()) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("§7§lPermissions:"));
                    for (String permission : reward.getPermissions()) {
                        lore.add(Component.text("§7- §f" + permission));
                    }
                }

                if (!reward.getCommands().isEmpty()) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("§7§lCommands:"));
                    for (String command : reward.getCommands()) {
                        lore.add(Component.text("§7- §f" + command));
                    }
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(statusPrefix + "Reward #" + instance));
                meta.lore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            }
        } else {
            // Display message if no rewards exist
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Component.text("§l§cNo rewards available!")
            ));
        }
    }

    // Helper method to calculate a specific join count for an instance
    private int calculateSpecificJoinCount(JoinStreakReward reward, float instance) {
        int min = reward.getMinRequiredJoins();
        int max = reward.getMaxRequiredJoins();

        // Special case for rewards with join count of -1 (special reward)
        if (min == -1) return -1;

        // If it's a single-value join requirement
        if (min == max) return min;

        // For a range, we need to map the instance to a specific value within the range
        // First, we need to determine how many total instances are in this range
        // This is assuming the instances are evenly distributed across the range
        Map<Integer, LinkedHashSet<Float>> joinRewardsMap = rewardsManager.getJoinRewardsMap();
        LinkedHashSet<Float> instances = joinRewardsMap.get(reward.getId());

        if (instances == null || instances.isEmpty()) return min;

        // Sort instances for consistent ordering
        List<Float> sortedInstances = new ArrayList<>(instances);
        Collections.sort(sortedInstances);

        // Find index of current instance
        int index = sortedInstances.indexOf(instance);
        if (index == -1) return min; // Fallback if instance not found

        // Calculate the specific join count based on position in range
        int totalInstances = sortedInstances.size();
        int rangeSize = max - min + 1;

        // If we have fewer instances than the range size, distribute them evenly
        if (totalInstances <= rangeSize) {
            // Calculate the step size to distribute instances across the range
            double step = (double) rangeSize / totalInstances;
            return min + (int)(index * step);
        } else {
            // If we have more instances than range size, multiple instances will share the same join count
            // This is a fallback case and might not match your exact requirements
            return min + (index * rangeSize / totalInstances);
        }
    }

    private ItemStack createGuiItem(Material material, @Nullable TextComponent name, @Nullable TextComponent...lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        ArrayList<Component> metalore = new ArrayList<>();
        if (lore != null) {
            metalore.addAll(Arrays.asList(lore));
        }

        meta.lore(metalore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, @NotNull InventoryAction action, @NotNull InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        // Prevent rapid clicking
        if (isOnCooldown(whoClicked)) {
            whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Handle pagination buttons
        if (slot == NEXT_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            openInventory(currentPage + 1);
            return;
        }

        if (slot == PREV_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            openInventory(currentPage - 1);
            return;
        }

        // Handle clicking on a reward
        if (clickedItem.getItemMeta().hasDisplayName()) {
            String displayName = Utils.stripColor(clickedItem.getItemMeta().getDisplayName());

            // Only process if it's an available reward
            if (displayName.contains("[CLICK TO CLAIM]")) {
                // Extract reward instance from the lore
                List<Component> lore = clickedItem.getItemMeta().lore();
                if (lore != null) {
                    for (Component line : lore) {
                        String loreLine = Utils.stripColor(((TextComponent) line).content());
                        if (loreLine.startsWith("Reward ID:")) {
                            try {
                                // Extract the float instance value
                                float instance = Float.parseFloat(loreLine.substring(loreLine.indexOf(":") + 1).trim());

                                // Get the user's rewards to be claimed
                                Set<Float> rewardsToBeClaimed = dbUsersManager.getUserFromUUID(whoClicked.getUniqueId().toString()).getRewardsToBeClaimed();

                                // Verify that the reward is actually claimable
                                if (rewardsToBeClaimed.contains(instance)) {
                                    // Find the reward object based on the instance
                                    JoinStreakReward reward = null;
                                    for (RewardDisplayItem displayItem : displayItems) {
                                        if (displayItem.getInstance() == instance) {
                                            reward = displayItem.getReward();
                                            break;
                                        }
                                    }

                                    if (reward != null) {
                                        // Process the reward claim
                                        rewardsManager.processCompletedReward(whoClicked, reward, instance, true);

                                        // Send success message
                                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aYou have successfully claimed your reward!"));

                                        // Refresh the GUI
                                        openInventory(currentPage);
                                    } else {
                                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cCouldn't find the reward details!"));
                                        whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                    }
                                } else {
                                    whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cThis reward is not available to claim!"));
                                    whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                }
                                return; // Exit after processing
                            } catch (NumberFormatException e) {
                                // Invalid format, ignore
                                whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cAn error occurred while claiming this reward!"));
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
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
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof RewardsInfoGui) {
            e.setCancelled(true);

            RewardsInfoGui gui = (RewardsInfoGui) e.getInventory().getHolder();
            gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RewardsInfoGui) {
            // Optional: Perform cleanup or additional actions on inventory close
            UUID playerId = event.getPlayer().getUniqueId();
            lastInteractionTime.remove(playerId);
        }
    }
}