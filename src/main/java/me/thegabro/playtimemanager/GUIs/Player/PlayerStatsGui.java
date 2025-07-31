package me.thegabro.playtimemanager.GUIs.Player;

import me.thegabro.playtimemanager.GUIs.BaseCustomGUI;
import me.thegabro.playtimemanager.GUIs.InventoryListener;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
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
    private int guiSize;

    private final Map<Integer, String> slotItemTypes = new HashMap<>();

    public enum ViewType {
        OWNER,    // Player viewing their own stats
        PLAYER,   // Player viewing another player's stats
        STAFF     // Staff member viewing stats
    }

    private static final String PERMISSION_STAFF_VIEW = "playtime.others.stats.staff";

    public PlayerStatsGui(Player sender, DBUser subject, String sessionToken) {
        super(sender, sessionToken);
        this.config = GUIsConfiguration.getInstance();
        this.subject = subject;

        // Determine view type first to get the correct size
        ViewType viewType = getViewType();

        // Get GUI size for this view (validate and round to nearest valid size)
        this.guiSize = getGuiSizeForView(viewType);

        // Process title with placeholders
        String rawTitle = getTitleForView(viewType);
        String processedTitle = processPlaceholders(rawTitle, "title");
        inv = Bukkit.createInventory(this, guiSize, Utils.parseColors(processedTitle));
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

        // Create GUI borders if enabled for current view
        createBorders();

        // Create all configured items based on view type
        createConfigurableItems();
    }

    /**
     * Determine the view type based on permissions and context
     */
    private ViewType getViewType() {
        boolean isOwnStats = sender.getUniqueId().toString().equals(subject.getUuid());
        boolean hasStaffPermission = sender.hasPermission(PERMISSION_STAFF_VIEW);

        if (hasStaffPermission) {
            return ViewType.STAFF;
        }

        if (isOwnStats) {
            return ViewType.OWNER;
        }

        return ViewType.PLAYER;
    }

    /**
     * Get the GUI size for the current view type
     */
    private int getGuiSizeForView(ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String sizePath = "player-stats-gui.views." + viewKey + ".size";

        // Get configured size or default to 54
        int configuredSize = config.getOrDefaultInt(sizePath, 54);

        // Validate and round to nearest valid inventory size (9, 18, 27, 36, 45, 54)
        return validateInventorySize(configuredSize);
    }

    /**
     * Validate and round inventory size to nearest valid size
     */
    private int validateInventorySize(int size) {
        if (size <= 9) return 9;
        if (size <= 18) return 18;
        if (size <= 27) return 27;
        if (size <= 36) return 36;
        if (size <= 45) return 45;
        return 54; // Maximum size
    }

    /**
     * Get the title for the current view type
     */
    private String getTitleForView(ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String titlePath = "player-stats-gui.views." + viewKey + ".title";

        // Check for view-specific title first
        if (config.contains(titlePath)) {
            return config.getString(titlePath);
        }

        // Fall back to default title
        return config.getOrDefaultString("player-stats-gui.gui.title", "&6%PLAYER_NAME%'s Statistics");
    }

    /**
     * Check if borders are enabled for the current view
     */
    private boolean areBordersEnabledForView(ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String borderEnabledPath = "player-stats-gui.views." + viewKey + ".border.enabled";

        // Check view-specific border setting first
        if (config.contains(borderEnabledPath)) {
            return config.getBoolean(borderEnabledPath);
        }

        // Fall back to global border setting
        return config.getOrDefaultBoolean("player-stats-gui.gui.border.enabled", true);
    }

    /**
     * Get border material for the current view
     */
    private Material getBorderMaterialForView(ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String borderMaterialPath = "player-stats-gui.views." + viewKey + ".border.material";

        String materialName;

        // Check view-specific border material first
        if (config.contains(borderMaterialPath)) {
            materialName = config.getString(borderMaterialPath);
        } else {
            // Fall back to global border material
            materialName = config.getOrDefaultString("player-stats-gui.gui.border.material", "BLACK_STAINED_GLASS_PANE");
        }

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "' for view " + viewType + ". Using BLACK_STAINED_GLASS_PANE.");
            return Material.BLACK_STAINED_GLASS_PANE;
        }
    }

    /**
     * Get border name for the current view
     */
    private String getBorderNameForView(ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String borderNamePath = "player-stats-gui.views." + viewKey + ".border.name";

        // Check view-specific border name first
        if (config.contains(borderNamePath)) {
            return config.getString(borderNamePath);
        }

        // Fall back to global border name
        return config.getOrDefaultString("player-stats-gui.gui.border.name", " ");
    }

    private void createBorders() {
        ViewType currentView = getViewType();

        // Check if borders are enabled for this view
        if (!areBordersEnabledForView(currentView)) {
            return;
        }

        // Get border configuration for current view
        Material borderMaterial = getBorderMaterialForView(currentView);
        String rawBorderName = getBorderNameForView(currentView);
        String borderName = processPlaceholders(rawBorderName, "border");

        // Calculate border slots based on GUI size
        Set<Integer> borderSlots = calculateBorderSlots(guiSize);

        for (int slot : borderSlots) {
            inv.setItem(slot, createGuiItem(borderMaterial, Utils.parseColors(borderName)));
            protectedSlots.add(slot);
        }
    }

    /**
     * Calculate which slots should be borders based on GUI size
     */
    private Set<Integer> calculateBorderSlots(int size) {
        Set<Integer> borderSlots = new HashSet<>();
        int rows = size / 9;

        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;

            // Top and bottom rows
            if (row == 0 || row == rows - 1) {
                borderSlots.add(i);
            }
            // Left and right columns (excluding corners already added)
            else if (col == 0 || col == 8) {
                borderSlots.add(i);
            }
        }

        return borderSlots;
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
                plugin.getLogger().warning("Invalid slot " + slot + " for item " + itemKey + " in view " + currentView + ". GUI size is " + inv.getSize() + ". Skipping item.");
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
     * and the enabled field is true (defaults to true if not specified)
     */
    private boolean isItemVisibleForView(String itemPath, ViewType viewType) {
        String viewKey = viewType.name().toLowerCase();
        String viewPath = itemPath + ".views." + viewKey;

        // Item is visible only if the view configuration exists
        if (!config.contains(viewPath)) {
            return false;
        }

        // Check if the view is enabled (defaults to true if not specified)
        String enabledPath = viewPath + ".enabled";
        return config.getOrDefaultBoolean(enabledPath, true);
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

        Map<String, String> combinations = new HashMap<>();

        addCommonPlaceholders(combinations);

        addTypeSpecialPlaceholders(combinations, itemType);


        return Utils.placeholdersReplacer(text, combinations);
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
        } else if (lastSeen != null && !lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));
            combinations.put("%LAST_SEEN_DATE%", lastSeen.format(formatter));

            Duration timeSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now());
            combinations.put("%TIME_SINCE_LAST_SEEN%", Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20));
        } else {
            combinations.put("%LAST_SEEN_DATE%", "Unknown");
            combinations.put("%TIME_SINCE_LAST_SEEN%", "Unknown");
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

        //Playtime Leaderboard
        int position = DBUsersManager.getInstance().getTopPlayers().indexOf(OnlineUsersManager.getInstance().getOnlineUser(subject.getNickname()));
        if(position != -1)
            combinations.put("%POSITION%", String.valueOf(position + 1));
        else
            combinations.put("%POSITION%", config.getOrDefaultString("player-stats-gui.leaderboard-settings.not-in-leaderboard-position", "-"));

    }

    private void addTypeSpecialPlaceholders(Map<String, String> combinations, String itemType) {
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

                    String goalFormat = config.getOrDefaultString("player-stats-gui.goals-settings.list-format", "&7- &e%GOAL%");
                    goalsList.append(goalFormat.replace("%GOAL%", completedGoals.get(i)));
                }

                combinations.put("%GOALS_LIST%", goalsList.toString());
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

        String itemType = slotItemTypes.get(slot);
        if (itemType == null) {
            return;
        }
    }
}