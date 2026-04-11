package me.thegabro.playtimemanager.GUIs.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.GUIs.BaseCustomGUI;
import me.thegabro.playtimemanager.GUIs.InventoryListener;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.JoinStreaksManager;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardExecutor;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
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
    private final RewardRegistry rewardRegistry = RewardRegistry.getInstance();
    private final RewardExecutor rewardExecutor = RewardExecutor.getInstance();
    private final GUIsConfiguration config;
    private final boolean isOwner;
    private final DBUser subject;
    private OfflinePlayer resolvedOfflinePlayer;
    private int currentPage = 0;
    private final List<RewardDisplayItem> allDisplayItems = new ArrayList<>();
    private final List<RewardDisplayItem> filteredDisplayItems = new ArrayList<>();
    private final int REWARDS_PER_PAGE = 28;

    private final int NEXT_BUTTON_SLOT;
    private final int PREV_BUTTON_SLOT;
    private final int PAGE_INDICATOR_SLOT;
    private final int SHOW_CLAIMED_BUTTON_SLOT;
    private final int SHOW_AVAILABLE_BUTTON_SLOT;
    private final int SHOW_LOCKED_BUTTON_SLOT;
    private final int CLAIM_ALL_BUTTON_SLOT;
    private final int NO_REWARDS_SLOT;

    private final Material FILTER_ACTIVE_MATERIAL;
    private final Material FILTER_INACTIVE_MATERIAL;

    private final Map<String, String> globalPlaceholders = new HashMap<>();

    private enum FilterType { CLAIMED, AVAILABLE, LOCKED }
    private FilterType currentFilter = FilterType.AVAILABLE;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public RewardsInfoGui(Player sender, DBUser subject, String sessionToken) {
        super(sender, sessionToken);
        this.config = GUIsConfiguration.getInstance();
        this.subject = subject;
        this.isOwner = sender.getName().equalsIgnoreCase(subject.getNickname());

        NEXT_BUTTON_SLOT           = config.getOrDefaultInt("rewards-gui.slots.next-page", 50);
        PREV_BUTTON_SLOT           = config.getOrDefaultInt("rewards-gui.slots.prev-page", 48);
        PAGE_INDICATOR_SLOT        = config.getOrDefaultInt("rewards-gui.slots.page-indicator", 49);
        SHOW_CLAIMED_BUTTON_SLOT   = config.getOrDefaultInt("rewards-gui.slots.filter-claimed", 3);
        SHOW_AVAILABLE_BUTTON_SLOT = config.getOrDefaultInt("rewards-gui.slots.filter-available", 4);
        SHOW_LOCKED_BUTTON_SLOT    = config.getOrDefaultInt("rewards-gui.slots.filter-locked", 5);
        CLAIM_ALL_BUTTON_SLOT      = config.getOrDefaultInt("rewards-gui.slots.claim-all", 46);
        NO_REWARDS_SLOT            = config.getOrDefaultInt("rewards-gui.slots.no-rewards", 21);

        FILTER_ACTIVE_MATERIAL   = parseMaterial(config.getString("rewards-gui.filters.active-material"), Material.LIME_DYE);
        FILTER_INACTIVE_MATERIAL = parseMaterial(config.getString("rewards-gui.filters.inactive-material"), Material.GRAY_DYE);

        int size = config.getOrDefaultInt("rewards-gui.gui.size", 54);
        String title = isOwner
                ? config.getString("rewards-gui.gui.title")
                : subject.getNickname() + "'s rewards";

        inv = Bukkit.createInventory(this, size, Utils.parseColors(title));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void openInventory() {
        currentPage = 0;
        resolvedOfflinePlayer = subject.isOnline()
                ? ((OnlineUser) subject).getPlayerInstance()
                : subject.getPlayerInstance();

        initializeGlobalPlaceholders();
        loadRewards();
        applyFilters();

        if (subject.isOnline()) {
            initializeItems();
            InventoryListener.getInstance().registerGUI(sender.getUniqueId(), this);
            sender.openInventory(inv);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                initializeItems();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InventoryListener.getInstance().registerGUI(sender.getUniqueId(), this);
                    if (sender.isOnline()) sender.openInventory(inv);
                });
            });
        }
    }

    public void changePage(int page) {
        currentPage = page;
        initializeItems();
        sender.updateInventory();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    // -------------------------------------------------------------------------
    // Core item builder — ALL items must pass through here
    // -------------------------------------------------------------------------

    /**
     * The single pipeline every item in this GUI must pass through.
     *
     * Steps:
     *   1. Resolve internal placeholders (%PLAYER_NAME%, %CURRENT_PAGE%, etc.)
     *   2. Resolve PlaceholderAPI placeholders
     *   3. Detect player-head icon and apply the correct skin
     *   4. Apply name + lore with italic stripped
     *   5. Optionally write PDC entries
     *
     * @param materialString  raw material name from config (may be PLAYER_HEAD or PLAYER_HEAD:name)
     * @param rawName         raw display-name string (may contain placeholders)
     * @param rawLore         raw lore lines (may contain placeholders); null = no lore
     * @param pdcEntries      optional map of NamespacedKey → String to write into the PDC; null = none
     */
    private ItemStack buildItem(String materialString,
                                String rawName,
                                @Nullable List<String> rawLore,
                                @Nullable Map<NamespacedKey, String> pdcEntries) {

        String resolvedName = applyPlaceholders(rawName);

        List<String> resolvedLore = new ArrayList<>();
        if (rawLore != null)
            for (String line : rawLore)
                resolvedLore.add(applyPlaceholders(line));

        String resolvedMaterial = applyPlaceholders(materialString);

        ItemStack item;
        if (isPlayerHead(resolvedMaterial)) {
            String[] parts = resolvedMaterial.split(":", 2);
            boolean hasSpecificSkin = parts.length > 1 && !parts[1].trim().isEmpty();
            item = hasSpecificSkin
                    ? Utils.createPlayerHead(resolvedMaterial)
                    : Utils.createPlayerHeadWithContext(resolvedMaterial, subject.getNickname(), resolvedOfflinePlayer);
        } else {
            item = new ItemStack(parseMaterial(resolvedMaterial, Material.PAPER));
        }

        // 4 — apply meta
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Utils.parseColors(resolvedName).decoration(TextDecoration.ITALIC, false));

        if (!resolvedLore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : resolvedLore)
                loreComponents.add(Utils.parseColors(line).decoration(TextDecoration.ITALIC, false));
            meta.lore(loreComponents);
        }

        // 5 — write PDC entries if provided
        if (pdcEntries != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            for (Map.Entry<NamespacedKey, String> entry : pdcEntries.entrySet())
                pdc.set(entry.getKey(), PersistentDataType.STRING, entry.getValue());
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Convenience overload — no PDC entries. */
    private ItemStack buildItem(String materialString, String rawName, @Nullable List<String> rawLore) {
        return buildItem(materialString, rawName, rawLore, null);
    }

    /** Convenience overload — no lore, no PDC entries. */
    private ItemStack buildItem(String materialString, String rawName) {
        return buildItem(materialString, rawName, null, null);
    }

    // -------------------------------------------------------------------------
    // Placeholder resolution (steps 1 + 2 of the pipeline)
    // -------------------------------------------------------------------------

    /**
     * Applies internal global placeholders first, then PlaceholderAPI.
     * Always safe to call — gracefully strips unresolved %tokens% when PAPI is unavailable.
     */
    private String applyPlaceholders(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;

        String result = text;
        for (Map.Entry<String, String> entry : globalPlaceholders.entrySet())
            result = result.replace(entry.getKey(), entry.getValue());

        if (plugin.isPlaceholdersAPIConfigured()) {
            try {
                result = PlaceholderAPI.setPlaceholders(resolvedOfflinePlayer, result);
            } catch (Exception e) {
                result = result.replaceAll("%[^%]+%", "");
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Placeholder maps
    // -------------------------------------------------------------------------

    private void initializeGlobalPlaceholders() {
        globalPlaceholders.clear();
        globalPlaceholders.put("%PLAYER_NAME%",     subject.getNickname());
        globalPlaceholders.put("%CURRENT_STREAK%",  String.valueOf(subject.getRelativeJoinStreak()));
        globalPlaceholders.put("%ABSOLUTE_STREAK%", String.valueOf(subject.getAbsoluteJoinStreak()));
    }

    private void updateDynamicPlaceholders() {
        int totalPages = (int) Math.ceil((double) filteredDisplayItems.size() / REWARDS_PER_PAGE);
        globalPlaceholders.put("%CURRENT_PAGE%", String.valueOf(currentPage + 1));
        globalPlaceholders.put("%TOTAL_PAGES%",  String.valueOf(Math.max(totalPages, 1)));
    }

    // -------------------------------------------------------------------------
    // Data loading & filtering
    // -------------------------------------------------------------------------

    public record RewardDisplayItem(JoinStreakReward reward, RewardSubInstance subInstance,
                                    RewardStatus status) implements Comparable<RewardDisplayItem> {
        @Override
        public int compareTo(RewardDisplayItem other) {
            if (this.status != other.status)
                return this.status.ordinal() - other.status.ordinal();
            if (this.subInstance.requiredJoins() != other.subInstance.requiredJoins() &&
                    this.subInstance.requiredJoins() != -1 && other.subInstance.requiredJoins() != -1)
                return Integer.compare(this.subInstance.requiredJoins(), other.subInstance.requiredJoins());
            if (this.reward.getId() != other.reward.getId())
                return Integer.compare(this.reward.getId(), other.reward.getId());
            return Integer.compare(this.subInstance.requiredJoins(), other.subInstance.requiredJoins());
        }
    }

    private enum RewardStatus { AVAILABLE_OLD, AVAILABLE, LOCKED, CLAIMED }

    private void applyFilters() {
        filteredDisplayItems.clear();
        for (RewardDisplayItem item : allDisplayItems) {
            switch (item.status()) {
                case CLAIMED      -> { if (currentFilter == FilterType.CLAIMED)   filteredDisplayItems.add(item); }
                case AVAILABLE_OLD,
                     AVAILABLE    -> { if (currentFilter == FilterType.AVAILABLE) filteredDisplayItems.add(item); }
                case LOCKED       -> { if (currentFilter == FilterType.LOCKED)    filteredDisplayItems.add(item); }
            }
        }
    }

    private boolean isReceived(List<RewardSubInstance> received, RewardSubInstance sub) {
        for (RewardSubInstance r : received)
            if (r.mainInstanceID().equals(sub.mainInstanceID()) && r.requiredJoins().equals(sub.requiredJoins()))
                return true;
        return false;
    }

    private boolean isToBeClaimed(List<RewardSubInstance> toBeClaimed, RewardSubInstance sub) {
        for (RewardSubInstance r : toBeClaimed)
            if (r.mainInstanceID().equals(sub.mainInstanceID()) && r.requiredJoins().equals(sub.requiredJoins()))
                return true;
        return false;
    }

    private void loadRewards() {
        allDisplayItems.clear();

        ArrayList<RewardSubInstance> joinRewardsMap = rewardRegistry.getJoinRewardsMap();
        List<RewardSubInstance> rewardsReceived     = subject.getReceivedRewards();
        List<RewardSubInstance> rewardsToBeClaimed  = subject.getRewardsToBeClaimed();

        for (RewardSubInstance subInstance : joinRewardsMap) {
            JoinStreakReward reward = rewardRegistry.getReward(subInstance.mainInstanceID());
            if (reward == null) continue;

            RewardStatus status;
            if      (isReceived(rewardsReceived, subInstance))       status = RewardStatus.CLAIMED;
            else if (isToBeClaimed(rewardsToBeClaimed, subInstance)) status = RewardStatus.AVAILABLE;
            else                                                      status = RewardStatus.LOCKED;

            allDisplayItems.add(new RewardDisplayItem(reward, subInstance, status));
        }

        for (RewardSubInstance subInstance : subject.getRewardsToBeClaimed()) {
            if (!subInstance.expired()) continue;

            JoinStreakReward reward = rewardRegistry.getReward(subInstance.mainInstanceID());
            if (reward == null) continue;

            boolean alreadyAdded = allDisplayItems.stream().anyMatch(item ->
                    item.subInstance().mainInstanceID().equals(subInstance.mainInstanceID()) &&
                            item.subInstance().requiredJoins().equals(subInstance.requiredJoins()));
            if (alreadyAdded) continue;

            allDisplayItems.add(new RewardDisplayItem(reward, subInstance, RewardStatus.AVAILABLE_OLD));
        }

        Collections.sort(allDisplayItems);
    }

    // -------------------------------------------------------------------------
    // GUI rendering
    // -------------------------------------------------------------------------

    public void initializeItems() {
        int leftIndex = 9, rightIndex = 17;
        protectedSlots.clear();
        inv.clear();

        updateDynamicPlaceholders();

        // Border
        if (config.getOrDefaultBoolean("rewards-gui.gui.border.enabled", true)) {
            String borderMaterialString = config.getOrDefaultString("rewards-gui.gui.border.material", "BLACK_STAINED_GLASS_PANE");
            String borderName           = config.getOrDefaultString("rewards-gui.gui.border.name", " ");
            int size = inv.getSize();

            for (int i = 0; i < size; i++) {
                if (i <= 9 || i >= size - 9 || i == leftIndex || i == rightIndex) {
                    safeSetItem(i, buildItem(borderMaterialString, borderName));
                    protectedSlots.add(i);
                    if (i == leftIndex)  leftIndex  += 9;
                    if (i == rightIndex) rightIndex += 9;
                }
            }
        }

        createFilterButtons();

        if (isOwner)
            safeSetProtected(CLAIM_ALL_BUTTON_SLOT, buildClaimAllButton());

        int totalPages = (int) Math.ceil((double) filteredDisplayItems.size() / REWARDS_PER_PAGE);
        if (totalPages > 1) {
            safeSetProtected(PAGE_INDICATOR_SLOT, buildPageIndicator());
            safeSetProtected(NEXT_BUTTON_SLOT, currentPage < totalPages - 1
                    ? buildNavButton("rewards-gui.pagination.next-page")
                    : buildNavButton("rewards-gui.pagination.next-page-disabled"));
            safeSetProtected(PREV_BUTTON_SLOT, currentPage > 0
                    ? buildNavButton("rewards-gui.pagination.prev-page")
                    : buildNavButton("rewards-gui.pagination.prev-page-disabled"));
        }

        if (!filteredDisplayItems.isEmpty()) {
            int startIndex = currentPage * REWARDS_PER_PAGE;
            int endIndex   = Math.min(startIndex + REWARDS_PER_PAGE, filteredDisplayItems.size());
            List<RewardDisplayItem> pageRewards = filteredDisplayItems.subList(startIndex, endIndex);

            int slot = 10;
            for (RewardDisplayItem displayItem : pageRewards) {
                while (protectedSlots.contains(slot)) slot++;
                if (slot >= inv.getSize() - 9) break;
                safeSetItem(slot, buildRewardItem(displayItem));
                slot++;
            }
        } else {
            safeSetItem(NO_REWARDS_SLOT, buildItem(
                    config.getOrDefaultString("rewards-gui.no-rewards.material", "BARRIER"),
                    config.getString("rewards-gui.no-rewards.name"),
                    getStringOrList("rewards-gui.no-rewards.lore")));
        }
    }

    // -------------------------------------------------------------------------
    // Specific item builders — all delegate to buildItem()
    // -------------------------------------------------------------------------

    private void createFilterButtons() {
        createFilterButton(SHOW_CLAIMED_BUTTON_SLOT,   FilterType.CLAIMED,   "claimed");
        createFilterButton(SHOW_AVAILABLE_BUTTON_SLOT, FilterType.AVAILABLE, "available");
        createFilterButton(SHOW_LOCKED_BUTTON_SLOT,    FilterType.LOCKED,    "locked");
    }

    private void createFilterButton(int slot, FilterType type, String configKey) {
        boolean isActive = currentFilter == type;
        String materialString = isActive
                ? config.getOrDefaultString("rewards-gui.filters.active-material", "LIME_DYE")
                : config.getOrDefaultString("rewards-gui.filters.inactive-material", "GRAY_DYE");
        String nameKey = isActive
                ? "rewards-gui.filters." + configKey + ".enabled-name"
                : "rewards-gui.filters." + configKey + ".disabled-name";
        String lorePath = isActive
                ? "rewards-gui.filters." + configKey + ".lore-enabled"
                : "rewards-gui.filters." + configKey + ".lore-disabled";

        safeSetProtected(slot, buildItem(materialString, config.getString(nameKey), getStringOrList(lorePath)));
    }

    private ItemStack buildPageIndicator() {
        return buildItem(
                config.getOrDefaultString("rewards-gui.pagination.page-indicator.material", "PAPER"),
                config.getString("rewards-gui.pagination.page-indicator.name"));
    }

    private ItemStack buildNavButton(String path) {
        return buildItem(
                config.getOrDefaultString(path + ".material", "ARROW"),
                config.getString(path + ".name"),
                getStringOrList(path + ".lore"));
    }

    private ItemStack buildClaimAllButton() {
        return buildItem(
                config.getOrDefaultString("rewards-gui.claim-all.material", "CHEST"),
                config.getString("rewards-gui.claim-all.name"),
                getStringOrList("rewards-gui.claim-all.lore"));
    }

    private ItemStack buildRewardItem(RewardDisplayItem displayItem) {
        JoinStreakReward reward       = displayItem.reward();
        RewardSubInstance subInstance = displayItem.subInstance();
        int specificJoins             = subInstance.requiredJoins();

        String statusKey;
        String rewardType;
        switch (displayItem.status()) {
            case AVAILABLE_OLD, AVAILABLE -> { statusKey = "available"; rewardType = "CLAIMABLE"; }
            case CLAIMED                  -> { statusKey = "claimed";   rewardType = "CLAIMED";   }
            default                       -> { statusKey = "locked";    rewardType = "LOCKED";    }
        }

        // Inject per-reward placeholder before building
        globalPlaceholders.put("%REQUIRED_JOINS%", specificJoins == -1 ? "-" : String.valueOf(specificJoins));

        String materialString = reward.getItemIcon();
        String rawName        = config.getString("rewards-gui.reward-items." + statusKey + ".prefix");
        List<String> rawLore  = expandLoreTemplate(
                getStringOrList("rewards-gui.reward-items." + statusKey + ".lore"), reward);

        // PDC entries
        Map<NamespacedKey, String> pdc = new LinkedHashMap<>();
        pdc.put(new NamespacedKey(plugin, "reward_id"),   reward.getId() + "." + subInstance.requiredJoins());
        pdc.put(new NamespacedKey(plugin, "reward_type"), rewardType);

        return buildItem(materialString, rawName, rawLore, pdc);
    }

    // -------------------------------------------------------------------------
    // Lore template expansion
    // -------------------------------------------------------------------------

    /**
     * Expands %DESCRIPTION% and %REWARD_DESCRIPTION% into multiple lines before
     * the result is handed to buildItem() for placeholder resolution.
     */
    private List<String> expandLoreTemplate(List<String> template, JoinStreakReward reward) {
        List<String> result = new ArrayList<>();
        if (template == null) return result;

        for (String line : template) {
            if (line.contains("%DESCRIPTION%")) {
                if (!reward.getDescription().isEmpty())
                    for (String descLine : reward.getDescription().split("/n"))
                        result.add(line.replace("%DESCRIPTION%", descLine));
            } else if (line.contains("%REWARD_DESCRIPTION%")) {
                if (!reward.getRewardDescription().isEmpty())
                    for (String descLine : reward.getRewardDescription().split("/n"))
                        result.add(line.replace("%REWARD_DESCRIPTION%", descLine));
            } else {
                result.add(line);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem,
                           @NotNull InventoryAction action, @NotNull InventoryClickEvent event) {

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (slot == SHOW_CLAIMED_BUTTON_SLOT)   { currentFilter = FilterType.CLAIMED;    currentPage = 0; applyFilters(); initializeItems(); return; }
        if (slot == SHOW_AVAILABLE_BUTTON_SLOT) { currentFilter = FilterType.AVAILABLE;  currentPage = 0; applyFilters(); initializeItems(); return; }
        if (slot == SHOW_LOCKED_BUTTON_SLOT)    { currentFilter = FilterType.LOCKED;     currentPage = 0; applyFilters(); initializeItems(); return; }

        if (slot == CLAIM_ALL_BUTTON_SLOT && isOwner) { claimAllRewards(); return; }

        Material navMat = parseMaterial(config.getString("rewards-gui.pagination.next-page.material"), Material.ARROW);
        if (slot == NEXT_BUTTON_SLOT && clickedItem.getType() == navMat) { changePage(currentPage + 1); return; }
        if (slot == PREV_BUTTON_SLOT && clickedItem.getType() == navMat) { changePage(currentPage - 1); return; }

        if (clickedItem.hasItemMeta() && isOwner) {
            PersistentDataContainer container = clickedItem.getItemMeta().getPersistentDataContainer();
            NamespacedKey typeKey = new NamespacedKey(plugin, "reward_type");
            if (container.has(typeKey, PersistentDataType.STRING) &&
                    "CLAIMABLE".equals(container.get(typeKey, PersistentDataType.STRING))) {
                NamespacedKey idKey = new NamespacedKey(plugin, "reward_id");
                if (container.has(idKey, PersistentDataType.STRING)) {
                    claimReward(container.get(idKey, PersistentDataType.STRING));
                } else {
                    whoClicked.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                            config.getString("rewards-gui.messages.not-available")));
                    playSound(config.getString("rewards-gui.sounds.claim-error"), whoClicked);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reward claiming
    // -------------------------------------------------------------------------

    private void claimReward(String instance) {
        if (!validateSession()) { handleInvalidSession(); return; }

        String[] parts = instance.split("\\.");
        if (parts.length != 2) {
            plugin.getLogger().severe("Invalid reward instance format: " + instance);
            return;
        }

        try {
            int rewardId          = Integer.parseInt(parts[0]);
            int specificJoinCount = Integer.parseInt(parts[1]);

            if (!sender.hasPermission("playtime.joinstreak.claim")) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        config.getString("rewards-gui.messages.no-permission")));
                playSound(config.getString("rewards-gui.sounds.no-permission"), sender);
                return;
            }

            JoinStreakReward reward = rewardRegistry.getReward(rewardId);
            if (reward == null) {
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        config.getString("rewards-gui.messages.reward-not-found")));
                playSound(config.getString("rewards-gui.sounds.claim-error"), sender);
                return;
            }

            if (!(subject instanceof OnlineUser onlineUser)) return;

            try {
                rewardExecutor.processCompletedReward(onlineUser,
                        rewardRegistry.getSubInstance(rewardId, specificJoinCount));
                loadRewards(); applyFilters(); initializeItems();
                sender.updateInventory();
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing reward for " + sender.getName() + ": " + e.getMessage());
                e.printStackTrace();
                sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                        config.getString("rewards-gui.messages.error-processing")));
                playSound(config.getString("rewards-gui.sounds.claim-error"), sender);
            }

        } catch (NumberFormatException e) {
            plugin.getLogger().severe("Failed to parse reward instance: " + instance);
            e.printStackTrace();
        }
    }

    private void claimAllRewards() {
        if (!plugin.getSessionManager().validateSession(sender.getUniqueId(), sessionToken)) {
            plugin.getLogger().warning("Player " + sender.getName() + " attempted GUI action with invalid session token!");
            sender.closeInventory();
            return;
        }

        if (!sender.hasPermission("playtime.joinstreak.claim")) {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                    config.getString("rewards-gui.messages.no-permission")));
            playSound(config.getString("rewards-gui.sounds.no-permission"), sender);
            return;
        }

        List<RewardSubInstance> claimableRewards = subject.getRewardsToBeClaimed();
        if (claimableRewards.isEmpty()) return;
        if (!(subject instanceof OnlineUser onlineUser)) return;

        int claimedCount = 0;
        for (RewardSubInstance subInstance : claimableRewards) {
            rewardExecutor.processCompletedReward(onlineUser, subInstance);
            claimedCount++;
        }

        if (claimedCount > 0) {
            String message = config.getString("rewards-gui.messages.claimed-rewards")
                    .replace("%COUNT%", String.valueOf(claimedCount));
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " + message));
            playSound(config.getString("rewards-gui.sounds.claim-success"), sender);
            loadRewards(); applyFilters(); initializeItems();
        } else {
            sender.sendMessage(Utils.parseColors(config.getString("prefix") + " " +
                    config.getString("rewards-gui.messages.error-processing")));
            playSound(config.getString("rewards-gui.sounds.claim-error"), sender);
        }
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private List<String> getStringOrList(String path) {
        List<String> list = config.getStringList(path);
        if (list != null && !list.isEmpty()) return list;

        String single = config.getString(path);
        if (single != null && !single.isBlank() && !single.equals("[]")) return Collections.singletonList(single);

        return Collections.emptyList();
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        if (isPlayerHead(name)) return Material.PLAYER_HEAD;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + name + "' in GUIs config, using fallback: " + fallback);
            return fallback;
        }
    }

    private boolean isPlayerHead(String iconString) {
        return iconString != null && iconString.toUpperCase().startsWith("PLAYER_HEAD");
    }

    private void playSound(String soundName, Player player) {
        try {
            Sound sound = null;
            try { sound = (Sound) Sound.class.getField(soundName).get(null); }
            catch (NoSuchFieldException | IllegalAccessException ignored) {}
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 10.0f, 0.0f);
            } else {
                plugin.getLogger().warning("Could not find sound '" + soundName + "' for join streak reward");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to play sound '" + soundName + "': " + e.getMessage());
        }
    }

    private void safeSetItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private void safeSetProtected(int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
            protectedSlots.add(slot);
        }
    }
}