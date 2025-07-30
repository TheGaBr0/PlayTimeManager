package me.thegabro.playtimemanager.GUIs.Player;

import me.thegabro.playtimemanager.GUIs.BaseCustomGUI;
import me.thegabro.playtimemanager.GUIs.InventoryListener;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerStatsGui extends BaseCustomGUI {

    private Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GUIsConfiguration config;
    private DBUser subject;

    // Map to store item types by slot for click handling
    private final Map<Integer, String> slotItemTypes = new HashMap<>();

    // View types
    public enum ViewType {
        OWNER,    // Player viewing their own stats
        PLAYER,   // Player viewing another player's stats
        STAFF     // Staff member viewing stats
    }

    // Staff permission only
    private static final String PERMISSION_STAFF_VIEW = "playtime.stats.staff";

    public PlayerStatsGui(Player sender, DBUser subject, String sessionToken) {
        super(sender, sessionToken);
        this.config = GUIsConfiguration.getInstance();
        this.subject = subject;

        // Process title with placeholders
        String rawTitle = config.getString("player-stats-gui.gui.title");
        String processedTitle = processPlaceholders(rawTitle, "title");
        inv = Bukkit.createInventory(this, 54, Utils.parseColors(processedTitle));
    }

    public void openInventory() {
        initializeItems();

        // Track active GUIs
        InventoryListener.getInstance().registerGUI(sender.getUniqueId(), this);

        sender.openInventory(inv);
    }

    public void initializeItems() {
        protectedSlots.clear();
        slotItemTypes.clear();
        inv.clear();

        // Create GUI borders
        createBorders();

        // Create all configured items based on view type
        createConfigurableItems();
    }

    /**
     * Determine the view type based on permissions and context
     */
    private ViewType getViewType() {
        // Owner view has highest priority - if looking at own stats
        if (sender.getUniqueId().equals(subject.getUuid())) {
            return ViewType.OWNER;
        }

        // Staff view if has staff permission and looking at another player
        if (sender.hasPermission(PERMISSION_STAFF_VIEW)) {
            return ViewType.STAFF;
        }

        // Default to player view
        return ViewType.PLAYER;
    }

    private void createBorders() {
        String sectionPath = "player-stats-gui.gui.border";

        // Get border configuration with placeholder processing
        Material borderMaterial = Material.valueOf(config.getOrDefaultString(sectionPath + ".material", "BLACK_STAINED_GLASS_PANE"));
        String rawBorderName = config.getOrDefaultString(sectionPath + ".name", " ");
        String borderName = processPlaceholders(rawBorderName, "border");

        int leftIndex = 9;
        int rightIndex = 17;

        for (int i = 0; i < 54; i++) {
            if (i <= 9 || i >= 45 || i == leftIndex || i == rightIndex) {
                inv.setItem(i, createGuiItem(borderMaterial, Utils.parseColors(borderName)));
                protectedSlots.add(i);
                if (i == leftIndex) leftIndex += 9;
                if (i == rightIndex) rightIndex += 9;
            }
        }
    }

    private void createConfigurableItems() {
        ViewType currentView = getViewType();

        // Get all configured items
        Map<String, Object> items = config.getConfigurationSection("player-stats-gui.items").getValues(false);

        for (String itemKey : items.keySet()) {
            String itemPath = "player-stats-gui.items." + itemKey;

            // Check if item should be visible for this view
            if (!isItemVisibleForView(itemPath, currentView)) {
                continue;
            }

            // Get slot for the current view
            int slot = getSlotForView(itemPath, currentView);

            // Handle out-of-bounds slot ID
            if (slot < 0 || slot >= inv.getSize()) {
                plugin.getLogger().warning("Invalid slot " + slot + " for item " + itemKey + " in view " + currentView + ". Skipping item.");
                continue;
            }

            // Get material for the current view
            Material material = getMaterialForView(itemPath, currentView);
            if (material == null) {
                plugin.getLogger().warning("Invalid material for item " + itemKey + " in view " + currentView + ". Skipping item.");
                continue;
            }

            // Get name and lore for the current view
            String rawName = getNameForView(itemPath, currentView);
            List<String> loreConfig = getLoreForView(itemPath, currentView);

            // Create the item
            createStatItem(slot, material, rawName, loreConfig, itemKey);

            // Store item type for click handling
            slotItemTypes.put(slot, itemKey);
        }
    }

    /**
     * Check if an item should be visible for the current view type
     * Item is visible only if a view configuration exists for the current view type
     */
    private boolean isItemVisibleForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewPath = itemPath + ".views." + viewKey;

        // Item is visible only if the view configuration exists
        return config.contains(viewPath);
    }

    /**
     * Get the slot for an item in the current view
     */
    private int getSlotForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewSlotPath = itemPath + ".views." + viewKey + ".slot";

        // View-specific slot is required since we only show items with view configs
        return config.getInt(viewSlotPath);
    }

    /**
     * Get the material for an item in the current view
     */
    private Material getMaterialForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewMaterialPath = itemPath + ".views." + viewKey + ".material";

        String materialName;

        // Check for view-specific material first
        if (config.contains(viewMaterialPath)) {
            materialName = config.getString(viewMaterialPath);
        } else {
            // Fall back to default material if view-specific doesn't exist
            materialName = config.getString(itemPath + ".material");
        }

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the name for an item in the current view
     */
    private String getNameForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewNamePath = itemPath + ".views." + viewKey + ".name";

        // Check for view-specific name first
        if (config.contains(viewNamePath)) {
            return config.getString(viewNamePath);
        }

        // Fall back to default name
        return config.getOrDefaultString(itemPath + ".name", "");
    }

    /**
     * Get the lore for an item in the current view
     */
    private List<String> getLoreForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewLorePath = itemPath + ".views." + viewKey + ".lore";

        // Check for view-specific lore first
        if (config.contains(viewLorePath)) {
            return config.getStringList(viewLorePath);
        }

        // Fall back to default lore
        return config.getStringList(itemPath + ".lore");
    }

    private void createStatItem(int slot, Material material, String rawName, List<String> loreConfig, String itemType) {
        List<Component> lore = new ArrayList<>();

        // Process lore with placeholders
        for (String loreLine : loreConfig) {
            String processedLine = processPlaceholders(loreLine, itemType);
            lore.add(Utils.parseColors(processedLine));
        }

        // Process name with placeholders
        String processedName = processPlaceholders(rawName, itemType);

        inv.setItem(slot, createGuiItem(material, Utils.parseColors(processedName), lore.toArray(new Component[0])));
        protectedSlots.add(slot);
    }

    private String processPlaceholders(String text, String itemType) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Create placeholder combinations map
        Map<String, String> combinations = new HashMap<>();

        // Add all common placeholders regardless of item type
        addCommonPlaceholders(combinations);

        // Add type-specific placeholders
        addTypeSpecificPlaceholders(combinations, itemType);

        // Add view-specific placeholders
        addViewSpecificPlaceholders(combinations);

        return Utils.placeholdersReplacer(text, combinations);
    }

    /**
     * Add view-specific placeholders
     */
    private void addViewSpecificPlaceholders(Map<String, String> combinations) {
        ViewType currentView = getViewType();
        combinations.put("%VIEW_TYPE%", currentView.name());
        combinations.put("%IS_OWNER%", String.valueOf(currentView == ViewType.OWNER));
        combinations.put("%IS_STAFF%", String.valueOf(currentView == ViewType.STAFF));
        combinations.put("%IS_PLAYER%", String.valueOf(currentView == ViewType.PLAYER));
    }

    private void addCommonPlaceholders(Map<String, String> combinations) {
        // Player information
        combinations.put("%PLAYER_NAME%", subject.getNickname());
        combinations.put("%UUID%", subject.getUuid().toString());

        // Playtime information
        long totalPlaytime = subject.getPlaytime();
        long artificialPlaytime = subject.getArtificialPlaytime();
        long realPlaytime = totalPlaytime - artificialPlaytime;

        combinations.put("%PLAYTIME%", String.valueOf(totalPlaytime));
        combinations.put("%ACTUAL_PLAYTIME%", String.valueOf(realPlaytime));
        combinations.put("%ARTIFICIAL_PLAYTIME%", String.valueOf(artificialPlaytime));

        // First join information
        LocalDateTime firstJoin = subject.getFirstJoin();
        if (firstJoin != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));
            combinations.put("%FIRST_JOIN_DATE%", firstJoin.format(formatter));

            Duration accountAge = Duration.between(firstJoin, LocalDateTime.now());
            combinations.put("%ACCOUNT_AGE%", Utils.ticksToFormattedPlaytime(accountAge.getSeconds() * 20));
        } else {
            combinations.put("%FIRST_JOIN_DATE%", "Unknown");
            combinations.put("%ACCOUNT_AGE%", "Unknown");
        }

        // Last seen information
        LocalDateTime lastSeen = subject.getLastSeen();
        boolean isOnline = Bukkit.getPlayer(subject.getUuid()) != null;

        if (isOnline) {
            combinations.put("%LAST_SEEN_DATE%", "Currently Online");
            combinations.put("%TIME_SINCE_LAST_SEEN%", "0");
            combinations.put("%ONLINE_STATUS%", "Online");
        } else if (lastSeen != null && !lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));
            combinations.put("%LAST_SEEN_DATE%", lastSeen.format(formatter));

            Duration timeSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now());
            combinations.put("%TIME_SINCE_LAST_SEEN%", Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20));
            combinations.put("%ONLINE_STATUS%", "Offline");
        } else {
            combinations.put("%LAST_SEEN_DATE%", "Unknown");
            combinations.put("%TIME_SINCE_LAST_SEEN%", "Unknown");
            combinations.put("%ONLINE_STATUS%", "Unknown");
        }

        // Join streak information
        combinations.put("%RELATIVE_STREAK%", String.valueOf(subject.getRelativeJoinStreak()));
        combinations.put("%ABSOLUTE_STREAK%", String.valueOf(subject.getAbsoluteJoinStreak()));

        // Goals information
        ArrayList<String> completedGoals = subject.getCompletedGoals();
        combinations.put("%GOALS_COUNT%", String.valueOf(completedGoals.size()));

        // Current time/date
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));
        combinations.put("%CURRENT_DATE%", now.format(formatter));
        combinations.put("%CURRENT_TIME%", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void addTypeSpecificPlaceholders(Map<String, String> combinations, String itemType) {
        switch (itemType.toLowerCase()) {
            case "goals":
                // Handle goal list placeholders - display in rows of 3
                ArrayList<String> completedGoals = subject.getCompletedGoals();
                StringBuilder goalsList = new StringBuilder();

                for (int i = 0; i < completedGoals.size(); i++) {
                    if (i > 0 && i % 3 == 0) {
                        goalsList.append("\n"); // New line every 3 goals
                    } else if (i > 0) {
                        goalsList.append(" "); // Space between goals on same line
                    }

                    String goalFormat = config.getOrDefaultString("player-stats-gui.goals.list-format", "&7- &e%GOAL%");
                    goalsList.append(goalFormat.replace("%GOAL%", completedGoals.get(i)));
                }

                combinations.put("%GOALS_LIST%", goalsList.toString());
                break;

            case "title":
                // Add any title-specific placeholders here if needed
                break;

            case "border":
                // Add any border-specific placeholders here if needed
                break;
        }
    }

    private ItemStack createGuiItem(Material material, @Nullable Component name, @Nullable Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }

        ArrayList<Component> metalore = new ArrayList<>();
        if (lore != null) {
            for (Component loreLine : lore) {
                metalore.add(loreLine.decoration(TextDecoration.ITALIC, false));
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
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)) {
            return;
        }

        // Check if this slot has an item type associated with it
        String itemType = slotItemTypes.get(slot);
        if (itemType == null) {
            return;
        }
    }
}