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
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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

    private int currentPage = 0;
    private final List<RewardDisplayItem> allDisplayItems = new ArrayList<>();
    private final List<RewardDisplayItem> filteredDisplayItems = new ArrayList<>();
    private final int REWARDS_PER_PAGE = 28; // Maximum number of rewards per page
    private final int NEXT_BUTTON_SLOT = 50;
    private final int PREV_BUTTON_SLOT = 48;
    private final int PAGE_INDICATOR_SLOT = 49;
    private final int SHOW_CLAIMED_BUTTON_SLOT = 3;
    private final int SHOW_AVAILABLE_BUTTON_SLOT = 4;
    private final int SHOW_LOCKED_BUTTON_SLOT = 5;
    private final int CLAIM_ALL_BUTTON_SLOT = 46;
    // Filter states
    private boolean showClaimed = false;
    private boolean showAvailable = true;
    private boolean showLocked = true;

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
        loadRewards();
        applyFilters();
        initializeItems();
        player.openInventory(inv);
    }

    public void openInventory(int page) {
        currentPage = page;
        applyFilters(); // Apply filters to the display items
        initializeItems(); // Only reinitialize items, no need to reload rewards
        player.openInventory(inv);
    }

    public static class RewardDisplayItem implements Comparable<RewardDisplayItem> {
        private final JoinStreakReward reward;
        private final String instance;
        private final RewardStatus status;
        private final int specificJoinCount;
        private final int instanceNumber;
        private final int subInstanceNumber;

        public RewardDisplayItem(JoinStreakReward reward, String instance, RewardStatus status, int specificJoinCount) {
            int subInstanceNumber1;
            int instanceNumber1;
            this.reward = reward;
            this.instance = instance;
            this.status = status;
            this.specificJoinCount = specificJoinCount;

            // Parse instance numbers (e.g., "1.2" -> instanceNumber=1, subInstanceNumber=2)
            String[] parts = instance.split("\\.");
            if (parts.length >= 2) {
                try {
                    instanceNumber1 = Integer.parseInt(parts[0]);
                    subInstanceNumber1 = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    instanceNumber1 = 0;
                    subInstanceNumber1 = 0;
                }
            } else {
                instanceNumber1 = 0;
                subInstanceNumber1 = 0;
            }
            this.subInstanceNumber = subInstanceNumber1;
            this.instanceNumber = instanceNumber1;
        }

        public JoinStreakReward getReward() {
            return reward;
        }

        public String getInstance() {
            return instance;
        }

        public RewardStatus getStatus() {
            return status;
        }

        public int getSpecificJoinCount() {
            return specificJoinCount;
        }

        @Override
        public int compareTo(RewardDisplayItem other) {
            // First, sort by status: AVAILABLE, LOCKED, CLAIMED
            if (this.status != other.status) {
                return this.status.ordinal() - other.status.ordinal();
            }

            // Then sort by required joins
            if (this.specificJoinCount != other.specificJoinCount && this.specificJoinCount != -1 && other.specificJoinCount != -1) {
                return Integer.compare(this.specificJoinCount, other.specificJoinCount);
            }

            // If same required joins, sort by instance number (1.x)
            if (this.instanceNumber != other.instanceNumber) {
                return Integer.compare(this.instanceNumber, other.instanceNumber);
            }

            // Finally, sort by sub-instance number (x.1)
            return Integer.compare(this.subInstanceNumber, other.subInstanceNumber);
        }
    }

    private enum RewardStatus {
        AVAILABLE_OLD, // Should show first
        AVAILABLE,
        LOCKED,
        CLAIMED,   // Should show last
    }

    private void applyFilters() {
        filteredDisplayItems.clear();

        for (RewardDisplayItem item : allDisplayItems) {
            switch (item.getStatus()) {
                case CLAIMED:
                    if (showClaimed) filteredDisplayItems.add(item);
                    break;
                case AVAILABLE_OLD:
                case AVAILABLE:
                    if (showAvailable) filteredDisplayItems.add(item);
                    break;
                case LOCKED:
                    if (showLocked) filteredDisplayItems.add(item);
                    break;
            }
        }
    }

    private void loadRewards() {
        allDisplayItems.clear();
        Map<Integer, LinkedHashSet<String>> joinRewardsMap = rewardsManager.getJoinRewardsMap();
        Set<String> rewardsReceived = dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getReceivedRewards();
        Set<String> rewardsToBeClaimed = dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRewardsToBeClaimed();

        for (Map.Entry<Integer, LinkedHashSet<String>> entry : joinRewardsMap.entrySet()) {
            Integer rewardId = entry.getKey();
            LinkedHashSet<String> instances = entry.getValue();

            JoinStreakReward reward = rewardsManager.getMainInstance(String.valueOf(rewardId));
            if (reward == null) continue;

            for (String instance : instances) {
                RewardStatus status;

                if (rewardsReceived.contains(instance)) {
                    status = RewardStatus.CLAIMED;
                } else if (rewardsToBeClaimed.contains(instance)) {
                    status = RewardStatus.AVAILABLE;
                } else {
                    status = RewardStatus.LOCKED;
                }

                // Calculate specific join count for this instance
                int specificJoinCount = calculateSpecificJoinCount(reward, instance);

                allDisplayItems.add(new RewardDisplayItem(reward, instance, status, specificJoinCount));
            }
        }

        for (String instance : dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRewardsToBeClaimed()) {

            if(!instance.endsWith("R"))
                continue;

            JoinStreakReward reward = rewardsManager.getMainInstance(instance);
            if (reward == null) continue;

            RewardStatus status;

            status = RewardStatus.AVAILABLE_OLD;

            allDisplayItems.add(new RewardDisplayItem(reward, instance, status, -1));
        }
        // Sort the display items using the natural ordering (defined by Comparable)
        Collections.sort(allDisplayItems);
    }

    public void initializeItems() {
        int leftIndex = 9;
        int rightIndex = 17;

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

        createFilterButtons();

        inv.setItem(CLAIM_ALL_BUTTON_SLOT, createGuiItem(
                Material.CHEST,
                Component.text("§e§lClaim all")
        ));
        protectedSlots.add(CLAIM_ALL_BUTTON_SLOT);

        // Add pagination controls if needed
        int totalPages = (int) Math.ceil((double) filteredDisplayItems.size() / REWARDS_PER_PAGE);

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

        if(!filteredDisplayItems.isEmpty()) {
            // Calculate start and end indices for current page
            int startIndex = currentPage * REWARDS_PER_PAGE;
            int endIndex = Math.min(startIndex + REWARDS_PER_PAGE, filteredDisplayItems.size());

            // Get subset of rewards for current page
            List<RewardDisplayItem> currentPageRewards = filteredDisplayItems.subList(startIndex, endIndex);

            int slot = 10; // Start at first available slot after top border
            for(RewardDisplayItem displayItem : currentPageRewards) {
                // Find next available slot
                while(protectedSlots.contains(slot)) slot++;
                if(slot >= 45) break; // Stop before bottom border

                // Create reward item based on status
                JoinStreakReward reward = displayItem.getReward();
                String instance = displayItem.getInstance();

                Material material;
                String statusPrefix;
                List<Component> lore = new ArrayList<>();

                switch (displayItem.getStatus()) {
                    case AVAILABLE_OLD:
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

                if(!(displayItem.getStatus() == RewardStatus.AVAILABLE_OLD)){
                    int specificJoinCount = displayItem.getSpecificJoinCount();
                    lore.add(Component.text("§7Required Joins: §e" +
                            (specificJoinCount == -1 ? "-" : specificJoinCount)));

                    lore.add(Component.text("§7Your current join streak: " +
                            (dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRelativeJoinStreak() < specificJoinCount
                                    ? "§c" : "§a") +
                            dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRelativeJoinStreak())
                    );
                }

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
                meta.displayName(Component.text(statusPrefix));
                meta.lore(lore);
                NamespacedKey key = new NamespacedKey(plugin, "reward_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, instance);

                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            }
        } else {
            // Display message if no rewards exist or all are filtered out
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Component.text("§l§cNo rewards to display!"),
                    Component.text("§7Try changing your filters")
            ));
        }
    }

    // Create the filter toggle buttons
    private void createFilterButtons() {
        // Show Claimed Button
        Material claimedMaterial = showClaimed ? Material.LIME_DYE : Material.GRAY_DYE;
        String claimedStatus = showClaimed ? "§a§lON" : "§c§lOFF";
        inv.setItem(SHOW_CLAIMED_BUTTON_SLOT, createGuiItem(
                claimedMaterial,
                Component.text("§8§l[CLAIMED] §7Rewards: " + claimedStatus),
                Component.text("§7Click to " + (showClaimed ? "hide" : "show") + " claimed rewards")
        ));
        protectedSlots.add(SHOW_CLAIMED_BUTTON_SLOT);

        // Show Available Button
        Material availableMaterial = showAvailable ? Material.LIME_DYE : Material.GRAY_DYE;
        String availableStatus = showAvailable ? "§a§lON" : "§c§lOFF";
        inv.setItem(SHOW_AVAILABLE_BUTTON_SLOT, createGuiItem(
                availableMaterial,
                Component.text("§a§l[AVAILABLE] §7Rewards: " + availableStatus),
                Component.text("§7Click to " + (showAvailable ? "hide" : "show") + " available rewards")
        ));
        protectedSlots.add(SHOW_AVAILABLE_BUTTON_SLOT);

        // Show Locked Button
        Material lockedMaterial = showLocked ? Material.LIME_DYE : Material.GRAY_DYE;
        String lockedStatus = showLocked ? "§a§lON" : "§c§lOFF";
        inv.setItem(SHOW_LOCKED_BUTTON_SLOT, createGuiItem(
                lockedMaterial,
                Component.text("§c§l[LOCKED] §7Rewards: " + lockedStatus),
                Component.text("§7Click to " + (showLocked ? "hide" : "show") + " locked rewards")
        ));
        protectedSlots.add(SHOW_LOCKED_BUTTON_SLOT);
    }

    private int calculateSpecificJoinCount(JoinStreakReward reward, String instance) {
        int min = reward.getMinRequiredJoins();
        int max = reward.getMaxRequiredJoins();

        // Special case for rewards with join count of -1 (special reward)
        if (min == -1) return -1;

        // If it's a single-value join requirement
        if (min == max) return min;

        // Get the instances for this reward
        Map<Integer, LinkedHashSet<String>> joinRewardsMap = rewardsManager.getJoinRewardsMap();
        LinkedHashSet<String> instances = joinRewardsMap.get(reward.getId());

        if (instances == null || instances.isEmpty()) return min;

        // Sort instances for consistent ordering
        List<String> sortedInstances = new ArrayList<>(instances);
        Collections.sort(sortedInstances, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                try {
                    // Split the instance strings at the decimal point
                    String[] aParts = a.split("\\.");
                    String[] bParts = b.split("\\.");

                    // Compare the integer parts first
                    int aIntPart = Integer.parseInt(aParts[0]);
                    int bIntPart = Integer.parseInt(bParts[0]);

                    if (aIntPart != bIntPart) {
                        return Integer.compare(aIntPart, bIntPart);
                    }

                    // If integer parts are equal, compare the decimal parts
                    int aDecimalPart = Integer.parseInt(aParts[1]);
                    int bDecimalPart = Integer.parseInt(bParts[1]);

                    return Integer.compare(aDecimalPart, bDecimalPart);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    return a.compareTo(b);
                }
            }
        });

        // Find index of current instance (0-based)
        int index = sortedInstances.indexOf(instance);
        if (index == -1) return min; // Fallback if instance not found

        // Calculate specific join count based on position in the sorted list
        return min + index;
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

        // Handle filter buttons
        if (slot == SHOW_CLAIMED_BUTTON_SLOT) {
            showClaimed = !showClaimed;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == SHOW_AVAILABLE_BUTTON_SLOT) {
            showAvailable = !showAvailable;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == SHOW_LOCKED_BUTTON_SLOT) {
            showLocked = !showLocked;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == CLAIM_ALL_BUTTON_SLOT){
            claimAllRewards();
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
                NamespacedKey key = new NamespacedKey(plugin, "reward_id");
                PersistentDataContainer container = clickedItem.getItemMeta().getPersistentDataContainer();
                if (container.has(key, PersistentDataType.STRING)) {
                    String instance = container.get(key, PersistentDataType.STRING);
                    Set<String> rewardsToBeClaimed = dbUsersManager.getUserFromUUID(whoClicked.getUniqueId().toString()).getRewardsToBeClaimed();

                    if (rewardsToBeClaimed.contains(instance)) {
                        JoinStreakReward reward = null;
                        for (RewardDisplayItem displayItem : allDisplayItems) {
                            if (displayItem.getInstance().equals(instance)) {
                                reward = displayItem.getReward();
                                break;
                            }
                        }

                        if (reward != null) {
                            rewardsManager.processCompletedReward(whoClicked, reward, instance, true);
                            whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aYou have successfully claimed your reward!"));

                            // Reload rewards to update status
                            loadRewards();
                            applyFilters();
                            openInventory(currentPage);
                        } else {
                            whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cCouldn't find the reward details!"));
                            whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    } else {
                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cThis reward is not available to claim!"));
                        whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }
    }

    private void claimAllRewards(){

        Set<String> claimableRewards = dbUsersManager.getUserFromUUID(player.getUniqueId().toString()).getRewardsToBeClaimed();

        if(claimableRewards.isEmpty())
            return;

        for(String instance : claimableRewards){
            rewardsManager.processCompletedReward(player, rewardsManager.getMainInstance(instance), instance, true);
        }
        loadRewards();
        applyFilters();
        openInventory(0);
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