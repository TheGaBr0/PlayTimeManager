package me.thegabro.playtimemanager.GUIs.Player;

import me.thegabro.playtimemanager.GUIs.BaseCustomGUI;
import me.thegabro.playtimemanager.GUIs.InventoryListener;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RewardsInfoGui extends BaseCustomGUI {

    private final Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final JoinStreaksManager rewardsManager = JoinStreaksManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final GUIsConfiguration config;
    private final boolean isOwner;
    private final DBUser subject;
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

    private final Map<String, String> globalPlaceholders = new HashMap<>();

    private enum FilterType {
        CLAIMED,
        AVAILABLE,
        LOCKED
    }
    private FilterType currentFilter = FilterType.AVAILABLE;

    public RewardsInfoGui(Player sender, DBUser subject, String sessionToken) {
        super(sender, sessionToken);
        this.config = GUIsConfiguration.getInstance();
        this.subject = subject;
        this.isOwner = sender.getName().equalsIgnoreCase(subject.getNickname());

        if(isOwner)
            inv = Bukkit.createInventory(this, 54, Utils.parseColors(config.getString("rewards-gui.gui.title")));
        else
            inv = Bukkit.createInventory(this, 54, Utils.parseColors(subject.getNickname()+"'s rewards"));

    }

    public void openInventory() {
        currentPage = 0; // Reset to first page when opening the GUI
        initializeGlobalPlaceholders();
        loadRewards();
        applyFilters();
        initializeItems();

        // Track active GUIs
        InventoryListener.getInstance().registerGUI(sender.getUniqueId(), this);

        sender.openInventory(inv);
    }

    public void changePage(int page) {
        currentPage = page;
        initializeItems();

        sender.updateInventory();

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
        AVAILABLE_OLD,
        AVAILABLE,
        LOCKED,
        CLAIMED,
    }

    private void applyFilters() {
        filteredDisplayItems.clear();

        for (RewardDisplayItem item : allDisplayItems) {
            switch (item.getStatus()) {
                case CLAIMED:
                    if (currentFilter == FilterType.CLAIMED) filteredDisplayItems.add(item);
                    break;
                case AVAILABLE_OLD:
                case AVAILABLE:
                    if (currentFilter == FilterType.AVAILABLE) filteredDisplayItems.add(item);
                    break;
                case LOCKED:
                    if (currentFilter == FilterType.LOCKED) filteredDisplayItems.add(item);
                    break;
            }
        }
    }

    private void loadRewards() {
        allDisplayItems.clear();
        Map<Integer, LinkedHashSet<String>> joinRewardsMap = rewardsManager.getRewardRegistry().getJoinRewardsMap();
        Set<String> rewardsReceived = subject.getReceivedRewards();
        Set<String> rewardsToBeClaimed = subject.getRewardsToBeClaimed();

        for (Map.Entry<Integer, LinkedHashSet<String>> entry : joinRewardsMap.entrySet()) {
            Integer rewardId = entry.getKey();
            LinkedHashSet<String> instances = entry.getValue();

            JoinStreakReward reward = rewardsManager.getRewardRegistry().getMainInstance(String.valueOf(rewardId));
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

        for (String instance : subject.getRewardsToBeClaimed()) {

            if (!instance.endsWith("R"))
                continue;

            JoinStreakReward reward = rewardsManager.getRewardRegistry().getMainInstance(instance);
            if (reward == null) continue;

            RewardStatus status;

            status = RewardStatus.AVAILABLE_OLD;

            int specificJoinCount = calculateSpecificJoinCount(reward, instance.replace(".R", ""));

            allDisplayItems.add(new RewardDisplayItem(reward, instance, status, specificJoinCount));
        }
        // Sort the display items using the natural ordering (defined by Comparable)
        Collections.sort(allDisplayItems);
    }

    public void initializeItems() {
        int leftIndex = 9, rightIndex = 17;
        protectedSlots.clear();
        inv.clear();

        // Update dynamic placeholders for pagination
        updateDynamicPlaceholders();

        // Create borders
        for (int i = 0; i < 54; i++) {
            if (i <= 9 || i >= 45 || i == leftIndex || i == rightIndex) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE,
                        Utils.parseColors(config.getString("rewards-gui.gui.border-item-name"))));
                protectedSlots.add(i);
                if (i == leftIndex) leftIndex += 9;
                if (i == rightIndex) rightIndex += 9;
            }
        }

        createFilterButtons();

        if(isOwner){
            inv.setItem(CLAIM_ALL_BUTTON_SLOT, createGuiItem(Material.CHEST,
                    Utils.parseColors(config.getString("rewards-gui.claim-all.name"))));
            protectedSlots.add(CLAIM_ALL_BUTTON_SLOT);
        }

        // Pagination with dynamic translation
        int totalPages = (int) Math.ceil((double) filteredDisplayItems.size() / REWARDS_PER_PAGE);
        if (totalPages > 1) {
            // Use the efficient translateDynamic method
            inv.setItem(PAGE_INDICATOR_SLOT, createGuiItem(Material.PAPER,
                    Utils.parseColors(translateDynamic(config.getString("rewards-gui.pagination.page-indicator")))));
            protectedSlots.add(PAGE_INDICATOR_SLOT);

            if (currentPage < totalPages - 1) {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(Material.ARROW,
                        Utils.parseColors(config.getString("rewards-gui.pagination.next-page.name")),
                        Utils.parseColors(config.getString("rewards-gui.pagination.next-page.lore"))));
            } else {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(Material.BARRIER,
                        Utils.parseColors(config.getString("rewards-gui.pagination.no-more-pages"))));
            }
            protectedSlots.add(NEXT_BUTTON_SLOT);

            if (currentPage > 0) {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(Material.ARROW,
                        Utils.parseColors(config.getString("rewards-gui.pagination.prev-page.name")),
                        Utils.parseColors(config.getString("rewards-gui.pagination.prev-page.lore"))));
            } else {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(Material.BARRIER,
                        Utils.parseColors(config.getString("rewards-gui.pagination.first-page"))));
            }
            protectedSlots.add(PREV_BUTTON_SLOT);
        }

        if (!filteredDisplayItems.isEmpty()) {
            int startIndex = currentPage * REWARDS_PER_PAGE;
            int endIndex = Math.min(startIndex + REWARDS_PER_PAGE, filteredDisplayItems.size());
            List<RewardDisplayItem> currentPageRewards = filteredDisplayItems.subList(startIndex, endIndex);

            int slot = 10;
            for (RewardDisplayItem displayItem : currentPageRewards) {
                while (protectedSlots.contains(slot)) slot++;
                if (slot >= 45) break;

                JoinStreakReward reward = displayItem.getReward();
                int specificJoins = displayItem.getSpecificJoinCount();
                int currentStreak = subject.getRelativeJoinStreak();

                Material material = Material.valueOf(reward.getItemIcon());
                String statusPrefix, rewardType;
                List<String> lore = new ArrayList<>();

                switch (displayItem.getStatus()) {
                    case AVAILABLE_OLD:
                    case AVAILABLE:
                        statusPrefix = translateDynamic(config.getString("rewards-gui.reward-items.available.prefix"));
                        lore.addAll(translateDynamic(config.getStringList("rewards-gui.reward-items.available.lore")));
                        rewardType = "CLAIMABLE";
                        break;
                    case CLAIMED:
                        statusPrefix = translateDynamic(config.getString("rewards-gui.reward-items.claimed.prefix"));
                        lore.addAll(translateDynamic(config.getStringList("rewards-gui.reward-items.claimed.lore")));
                        rewardType = "CLAIMED";
                        break;
                    case LOCKED:
                    default:
                        statusPrefix = translateDynamic(config.getString("rewards-gui.reward-items.locked.prefix"));
                        lore.addAll(translateDynamic(config.getStringList("rewards-gui.reward-items.locked.lore")));
                        rewardType = "LOCKED";
                        break;
                }

                // Add required joins info with individual placeholder replacement
                String requiredJoinsText = translateDynamic(config.getString("rewards-gui.reward-items.info-lore.required-joins"));
                requiredJoinsText = quickTranslate(requiredJoinsText, "%REQUIRED_JOINS%", specificJoins == -1 ? "-" : String.valueOf(specificJoins));
                lore.add(requiredJoinsText);

                if (!(displayItem.getStatus() == RewardStatus.AVAILABLE_OLD) && !(displayItem.getStatus() == RewardStatus.AVAILABLE)) {
                    // Handle join streak color dynamically
                    String streakColor = currentStreak < specificJoins ?
                            config.getString("rewards-gui.reward-items.info-lore.join-streak-color.insufficient") :
                            config.getString("rewards-gui.reward-items.info-lore.join-streak-color.sufficient");

                    String joinStreakText = translateDynamic(config.getString("rewards-gui.reward-items.info-lore.join-streak"));
                    joinStreakText = quickTranslate(joinStreakText, "%JOIN_STREAK_COLOR%", streakColor);
                    joinStreakText = quickTranslate(joinStreakText, "%COLOR%", streakColor);
                    lore.add(joinStreakText);
                }

                // Handle description with line-by-line translation
                if (!reward.getDescription().isEmpty()) {
                    lore.add(translateDynamic(config.getString("rewards-gui.reward-items.info-lore.description-separator")));
                    String descTemplate = config.getString("rewards-gui.reward-items.info-lore.description");
                    for (String line : reward.getDescription().split("/n")) {
                        String translatedLine = translateDynamic(descTemplate);
                        translatedLine = quickTranslate(translatedLine, "%DESCRIPTION%", line);
                        lore.add(translatedLine);
                    }
                }

                // Handle reward description with line-by-line translation
                if (!reward.getRewardDescription().isEmpty()) {
                    lore.add(translateDynamic(config.getString("rewards-gui.reward-items.info-lore.reward-description-separator")));
                    String rewardDescTemplate = config.getString("rewards-gui.reward-items.info-lore.reward-description");
                    for (String line : reward.getRewardDescription().split("/n")) {
                        String translatedLine = translateDynamic(rewardDescTemplate);
                        translatedLine = quickTranslate(translatedLine, "%REWARD_DESCRIPTION%", line);
                        lore.add(translatedLine);
                    }
                }

                List<Component> componentLore = new ArrayList<>();
                for (String loreLine : lore) componentLore.add(Utils.parseColors(loreLine));

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Utils.parseColors(statusPrefix).decoration(TextDecoration.ITALIC, false));

                ArrayList<Component> metalore = new ArrayList<>();
                for (Component loreLine : componentLore) metalore.add(loreLine.decoration(TextDecoration.ITALIC, false));
                meta.lore(metalore);

                NamespacedKey idKey = new NamespacedKey(plugin, "reward_id");
                NamespacedKey typeKey = new NamespacedKey(plugin, "reward_type");
                meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, displayItem.getInstance());
                meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, rewardType);

                item.setItemMeta(meta);
                inv.setItem(slot, item);
                slot++;
            }
        } else {
            inv.setItem(22, createGuiItem(Material.BARRIER,
                    Utils.parseColors(config.getString("rewards-gui.no-rewards.name")),
                    Utils.parseColors(config.getString("rewards-gui.no-rewards.lore"))));
        }
    }

    // Create the filter toggle buttons
    private void createFilterButtons() {
        // Show Claimed Button
        Material claimedMaterial = currentFilter == FilterType.CLAIMED ? Material.LIME_DYE : Material.GRAY_DYE;
        String claimedName = currentFilter == FilterType.CLAIMED ?
                config.getString("rewards-gui.filters.claimed.enabled-name") :
                config.getString("rewards-gui.filters.claimed.disabled-name");
        String claimedLore = currentFilter == FilterType.CLAIMED ?
                config.getString("rewards-gui.filters.claimed.lore-enabled") :
                config.getString("rewards-gui.filters.claimed.lore-disabled");

        inv.setItem(SHOW_CLAIMED_BUTTON_SLOT, createGuiItem(
                claimedMaterial,
                Utils.parseColors(claimedName),
                Utils.parseColors(claimedLore)
        ));
        protectedSlots.add(SHOW_CLAIMED_BUTTON_SLOT);

        // Show Available Button
        Material availableMaterial = currentFilter == FilterType.AVAILABLE ? Material.LIME_DYE : Material.GRAY_DYE;
        String availableName = currentFilter == FilterType.AVAILABLE ?
                config.getString("rewards-gui.filters.available.enabled-name") :
                config.getString("rewards-gui.filters.available.disabled-name");
        String availableLore = currentFilter == FilterType.AVAILABLE ?
                config.getString("rewards-gui.filters.available.lore-enabled") :
                config.getString("rewards-gui.filters.available.lore-disabled");

        inv.setItem(SHOW_AVAILABLE_BUTTON_SLOT, createGuiItem(
                availableMaterial,
                Utils.parseColors(availableName),
                Utils.parseColors(availableLore)
        ));
        protectedSlots.add(SHOW_AVAILABLE_BUTTON_SLOT);

        // Show Locked Button
        Material lockedMaterial = currentFilter == FilterType.LOCKED ? Material.LIME_DYE : Material.GRAY_DYE;
        String lockedName = currentFilter == FilterType.LOCKED ?
                config.getString("rewards-gui.filters.locked.enabled-name") :
                config.getString("rewards-gui.filters.locked.disabled-name");
        String lockedLore = currentFilter == FilterType.LOCKED ?
                config.getString("rewards-gui.filters.locked.lore-enabled") :
                config.getString("rewards-gui.filters.locked.lore-disabled");

        inv.setItem(SHOW_LOCKED_BUTTON_SLOT, createGuiItem(
                lockedMaterial,
                Utils.parseColors(lockedName),
                Utils.parseColors(lockedLore)
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
        Map<Integer, LinkedHashSet<String>> joinRewardsMap = rewardsManager.getRewardRegistry().getJoinRewardsMap();
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

    private ItemStack createGuiItem(Material material, @Nullable Component name, @Nullable Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }

        ArrayList<Component> metalore = new ArrayList<>();
        if (lore != null) {
            // Disable italic for each lore line
            for (Component loreLine : lore) {
                metalore.add(loreLine.decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(metalore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRewardItem(Material material, String name, List<Component> lore, PersistentDataContainer originalContainer) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Utils.parseColors(name).decoration(TextDecoration.ITALIC, false));

        ArrayList<Component> metalore = new ArrayList<>();
        for (Component loreLine : lore) {
            metalore.add(loreLine.decoration(TextDecoration.ITALIC, false));
        }

        if (originalContainer != null) {
            for (NamespacedKey key : originalContainer.getKeys()) {
                if (originalContainer.has(key, PersistentDataType.STRING)) {
                    String value = originalContainer.get(key, PersistentDataType.STRING);
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                }
            }
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

        if (slot == SHOW_CLAIMED_BUTTON_SLOT) {
            currentFilter = FilterType.CLAIMED;
            currentPage=0;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == SHOW_AVAILABLE_BUTTON_SLOT) {
            currentFilter = FilterType.AVAILABLE;
            currentPage=0;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == SHOW_LOCKED_BUTTON_SLOT) {
            currentFilter = FilterType.LOCKED;
            currentPage=0;
            applyFilters();
            initializeItems();
            return;
        }

        if (slot == CLAIM_ALL_BUTTON_SLOT && isOwner) {
            claimAllRewards();
            return;
        }

        // Handle pagination buttons
        if (slot == NEXT_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            changePage(currentPage + 1);
            return;
        }

        if (slot == PREV_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            changePage(currentPage - 1);
            return;
        }

        // Handle clicking on a reward
        if (clickedItem.getItemMeta().hasDisplayName() && isOwner) {

            PersistentDataContainer container = clickedItem.getItemMeta().getPersistentDataContainer();
            NamespacedKey typeKey = new NamespacedKey(plugin, "reward_type");

            if (container.has(typeKey, PersistentDataType.STRING)) {
                if(container.get(typeKey, PersistentDataType.STRING).equals("CLAIMABLE")){
                    // Extract reward instance from the lore
                    NamespacedKey idKey = new NamespacedKey(plugin, "reward_id");

                    if (container.has(idKey, PersistentDataType.STRING)) {
                        String instance = container.get(idKey, PersistentDataType.STRING);
                        claimReward(instance);
                    } else {
                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                                config.getString("rewards-gui.messages.not-available")));
                        whoClicked.playSound(whoClicked.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
        }
    }

    private void claimReward(String instance) {
        if (!validateSession()) {
            handleInvalidSession();
            return;
        }

        if (!sender.hasPermission("playtime.joinstreak.claim")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("rewards-gui.messages.no-permission")));
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        JoinStreakReward reward = rewardsManager.getRewardRegistry().getMainInstance(instance);
        if (reward == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("rewards-gui.messages.reward-not-found")));
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        try {
            rewardsManager.getRewardExecutor().processCompletedReward(sender, reward, instance);

            loadRewards();
            applyFilters();
            initializeItems();
            sender.updateInventory();
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing reward for player " + sender.getName() + ": " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("rewards-gui.messages.error-processing")));
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private void claimAllRewards() {
        if (!plugin.getSessionManager().validateSession(sender.getUniqueId(), sessionToken)) {
            plugin.getLogger().warning("Player " + sender.getName() + " attempted GUI action with invalid session token!");
            sender.closeInventory();
            return;
        }

        if (!sender.hasPermission("playtime.joinstreak.claim")) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("rewards-gui.messages.no-permission")));
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Set<String> claimableRewards = new HashSet<>(subject.getRewardsToBeClaimed());

        if (claimableRewards.isEmpty()) {
            return;
        }

        int claimedCount = 0;
        for (String instance : claimableRewards) {
            JoinStreakReward reward = rewardsManager.getRewardRegistry().getMainInstance(instance);
            if (reward != null) {
                try {
                    rewardsManager.getRewardExecutor().processCompletedReward(sender, reward, instance);
                    claimedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Error processing reward for player " + sender.getName() + ": " + e.getMessage());
                }
            }
        }

        if (claimedCount > 0) {
            // Use efficient placeholder replacement for count
            String message = quickTranslate(
                    config.getString("rewards-gui.messages.claimed-rewards"),
                    "%COUNT%", String.valueOf(claimedCount)
            );
            // Also support legacy {count} format
            message = quickTranslate(message, "{count}", String.valueOf(claimedCount));

            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " + message));
            sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            // Reload the rewards to refresh the GUI
            loadRewards();
            applyFilters();
            initializeItems();
        } else {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("rewards-gui.messages.error-processing")));
            sender.playSound(sender.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    /**
     * Initialize global placeholders that don't change during the GUI session
     */
    private void initializeGlobalPlaceholders() {
        globalPlaceholders.clear();
        globalPlaceholders.put("%PLAYER_NAME%", subject.getNickname());
        globalPlaceholders.put("%CURRENT_STREAK%", String.valueOf(subject.getRelativeJoinStreak()));
        globalPlaceholders.put("%ABSOLUTE_STREAK%", String.valueOf(subject.getAbsoluteJoinStreak()));
    }

    /**
     * Update dynamic placeholders that change during GUI operations
     */
    private void updateDynamicPlaceholders() {
        int totalPages = (int) Math.ceil((double) filteredDisplayItems.size() / REWARDS_PER_PAGE);
        globalPlaceholders.put("%CURRENT_PAGE%", String.valueOf(currentPage + 1));
        globalPlaceholders.put("%TOTAL_PAGES%", String.valueOf(totalPages));
    }

    /**
     * Translate text with global placeholders
     */
    private String translateDynamic(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        // Apply global placeholders
        for (Map.Entry<String, String> entry : globalPlaceholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }
    /**
     * Translate a list of strings with global placeholders
     */
    private List<String> translateDynamic(List<String> list) {
        if (list == null || list.isEmpty()) return list;
        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(translateDynamic(s));
        }
        return result;
    }

    /**
     * Quick translate for single dynamic placeholders (most efficient for simple cases)
     */
    private String quickTranslate(String text, String placeholder, String value) {
        return text != null ? text.replace(placeholder, value) : text;
    }

}