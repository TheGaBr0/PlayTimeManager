package me.thegabro.playtimemanager.GUIs.Player;

import me.thegabro.playtimemanager.GUIs.BaseCustomGUI;
import me.thegabro.playtimemanager.GUIs.InventoryListener;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStatsGui extends BaseCustomGUI {

    private Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final GUIsConfiguration config;
    private DBUser subject;

    // Static map to track refresh cooldowns across all instances
    private static final Map<UUID, Long> refreshCooldowns = new ConcurrentHashMap<>();

    // Map to store item types by slot for click handling
    private final Map<Integer, String> slotItemTypes = new HashMap<>();

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

        // Create all configured items
        createConfigurableItems();
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
        // Get all configured items
        Map<String, Object> items = config.getConfigurationSection("player-stats-gui.items").getValues(false);

        for (String itemKey : items.keySet()) {
            String itemPath = "player-stats-gui.items." + itemKey;

            // Skip if item is disabled
            if (!config.getOrDefaultBoolean(itemPath + ".enabled", true)) {
                continue;
            }

            int slot = config.getInt(itemPath + ".slot");

            // Handle out-of-bounds slot ID
            if (slot < 0 || slot >= inv.getSize()) {
                plugin.getLogger().warning("Invalid slot " + slot + " for item " + itemKey + " in player-stats-gui. Skipping item.");
                continue;
            }

            String materialName = config.getString(itemPath + ".material");
            Material material;

            // Handle invalid material names
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + materialName + "' for item " + itemKey + " in player-stats-gui. Skipping item.");
                continue;
            }

            String rawName = config.getOrDefaultString(itemPath + ".name", "");
            List<String> loreConfig = config.getStringList(itemPath + ".lore");
            String permission = config.getOrDefaultString(itemPath + ".permission", "");

            // Create the item
            createStatItem(slot, material, rawName, loreConfig, permission, itemKey);

            // Store item type for click handling
            slotItemTypes.put(slot, itemKey);
        }
    }

    private void createStatItem(int slot, Material material, String rawName, List<String> loreConfig, String permission, String itemType) {
        List<Component> lore = new ArrayList<>();

        // Check permission if specified
        boolean hasPermission = permission.isEmpty() || sender.hasPermission(permission);

        if (!hasPermission) {
            // Show no permission message with placeholder processing
            String rawNoPermMsg = config.getOrDefaultString("player-stats-gui.messages.no-permission", "&cYou don't have permission to view this information.");
            String processedNoPermMsg = processPlaceholders(rawNoPermMsg, itemType);
            lore.add(Utils.parseColors(processedNoPermMsg));
        } else {
            // Process lore with placeholders
            for (String loreLine : loreConfig) {
                String processedLine = processPlaceholders(loreLine, itemType);
                lore.add(Utils.parseColors(processedLine));
            }
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
        combinations.put("%PLAYTIME_FORMATTED%", Utils.ticksToFormattedPlaytime(totalPlaytime));
        combinations.put("%ACTUAL_PLAYTIME_FORMATTED%", Utils.ticksToFormattedPlaytime(realPlaytime));
        combinations.put("%ARTIFICIAL_PLAYTIME_FORMATTED%", Utils.ticksToFormattedPlaytime(artificialPlaytime));

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
                // Handle goal list placeholders
                ArrayList<String> completedGoals = subject.getCompletedGoals();
                StringBuilder goalsList = new StringBuilder();
                int maxGoals = config.getOrDefaultInt("player-stats-gui.goals.max-display", 5);
                int count = 0;

                for (String goal : completedGoals) {
                    if (count >= maxGoals) break;
                    if (count > 0) goalsList.append("\n");
                    String goalFormat = config.getOrDefaultString("player-stats-gui.goals.list-format", "&7- &e%GOAL%");
                    goalsList.append(goalFormat.replace("%GOAL%", goal));
                    count++;
                }

                if (completedGoals.size() > maxGoals) {
                    String moreFormat = config.getOrDefaultString("player-stats-gui.goals.more-format", "&7... and &e%REMAINING% &7more");
                    goalsList.append("\n").append(moreFormat.replace("%REMAINING%", String.valueOf(completedGoals.size() - maxGoals)));
                }

                combinations.put("%GOALS_LIST%", goalsList.toString());
                break;

            case "refresh":
                int delay = config.getOrDefaultInt("player-stats-gui.items.refresh.delay", 3);
                combinations.put("%DELAY%", String.valueOf(delay));
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

        // Handle refresh button
        if ("refresh".equals(itemType)) {
            handleRefreshClick(whoClicked);
            return;
        }

        // All other slots are informational only
    }

    private void handleRefreshClick(Player player) {
        String itemPath = "player-stats-gui.items.refresh";

        // Check permission
        String permission = config.getOrDefaultString(itemPath + ".permission", "playtime.stats.refresh");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            return;
        }

        // Check cooldown
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        int delaySeconds = config.getOrDefaultInt(itemPath + ".delay", 3);
        long delayMillis = delaySeconds * 1000L;

        if (refreshCooldowns.containsKey(playerUUID)) {
            long lastRefresh = refreshCooldowns.get(playerUUID);
            long timeSinceLastRefresh = currentTime - lastRefresh;

            if (timeSinceLastRefresh < delayMillis) {
                return;
            }
        }

        // Update cooldown
        refreshCooldowns.put(playerUUID, currentTime);

        // Play refresh sound
        String soundName = config.getOrDefaultString(itemPath + ".sound", "UI_BUTTON_CLICK");
        double volume = config.getOrDefaultDouble(itemPath + ".sound-volume", 1.0);
        double pitch = config.getOrDefaultDouble(itemPath + ".sound-pitch", 1.0);

        try {
            Sound sound = (Sound) Sound.class.getField(soundName).get(null);

            if (sound != null) {
                player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
            }
        } catch (Exception ignored) {}

        // Perform refresh
        refreshStats();
    }

    private void refreshStats() {
        if (!validateSession()) {
            handleInvalidSession();
            return;
        }

        // Refresh the subject data from database
        subject = dbUsersManager.getUserFromUUID(subject.getUuid());

        if (subject == null) {
            sender.closeInventory();
            return;
        }

        // Update the inventory title with fresh placeholders
        String rawTitle = config.getString("player-stats-gui.gui.title");
        String processedTitle = processPlaceholders(rawTitle, "title");

        // Create new inventory with updated title
        Inventory newInv = Bukkit.createInventory(this, 54, Utils.parseColors(processedTitle));
        this.inv = newInv;

        // Rebuild the GUI with fresh data
        initializeItems();

        // Close current inventory and open the new one
        sender.closeInventory();
        sender.openInventory(inv);
    }
}