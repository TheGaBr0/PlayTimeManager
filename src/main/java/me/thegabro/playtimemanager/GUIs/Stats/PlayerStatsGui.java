package me.thegabro.playtimemanager.GUIs.Stats;

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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerStatsGui implements InventoryHolder, Listener {

    private Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final DBUsersManager dbUsersManager = DBUsersManager.getInstance();
    private final GUIsConfiguration config;
    private Player sender;
    private boolean isOwner;
    private DBUser subject;
    private final String sessionToken;

    // GUI Layout Constants
    private final int PLAYTIME_INFO_SLOT = 11;
    private final int FIRST_JOIN_INFO_SLOT = 13;
    private final int LAST_SEEN_INFO_SLOT = 15;
    private final int JOIN_STREAK_INFO_SLOT = 29;
    private final int GOALS_INFO_SLOT = 31;
    private final int ACCOUNT_OVERVIEW_SLOT = 33;
    private final int REFRESH_BUTTON_SLOT = 49;

    protected static boolean isListenerRegistered = false;
    protected static final Map<UUID, PlayerStatsGui> activeGuis = new HashMap<>();

    public PlayerStatsGui(Player sender, DBUser subject, String sessionToken) {
        this.sender = sender;
        this.sessionToken = sessionToken;
        this.config = GUIsConfiguration.getInstance();
        this.subject = subject;
        this.isOwner = sender.getName().equalsIgnoreCase(subject.getNickname());

        if(isOwner)
            inv = Bukkit.createInventory(this, 54, Utils.parseColors(config.getString("player-stats-gui.gui.title")));
        else
            inv = Bukkit.createInventory(this, 54, Utils.parseColors(subject.getNickname()+"'s Stats"));

        // Register listeners only once
        if (!isListenerRegistered) {
            Bukkit.getPluginManager().registerEvents(new InventoryListener(), PlayTimeManager.getInstance());
            isListenerRegistered = true;
        }
    }

    public void openInventory() {
        initializeItems();

        // Track active GUIs
        activeGuis.put(sender.getUniqueId(), this);

        sender.openInventory(inv);
    }

    public void initializeItems() {
        protectedSlots.clear();
        inv.clear();

        // Create GUI borders
        createBorders();

        // Create stat display items
        createPlaytimeInfoItem();
        createFirstJoinInfoItem();
        createLastSeenInfoItem();
        createJoinStreakInfoItem();
        createGoalsInfoItem();
        createAccountOverviewItem();

        // Add refresh button
        createRefreshButton();
    }

    private void createBorders() {
        int leftIndex = 9;
        int rightIndex = 17;

        for (int i = 0; i < 54; i++) {
            if (i <= 9 || i >= 45 || i == leftIndex || i == rightIndex) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE,
                        Utils.parseColors(config.getString("player-stats-gui.gui.border-item-name"))));
                protectedSlots.add(i);
                if (i == leftIndex) leftIndex += 9;
                if (i == rightIndex) rightIndex += 9;
            }
        }
    }

    private void createPlaytimeInfoItem() {
        long totalPlaytime = subject.getPlaytime();
        long artificialPlaytime = subject.getArtificialPlaytime();
        long realPlaytime = totalPlaytime - artificialPlaytime;

        List<Component> lore = new ArrayList<>();

        String totalPlaytimeStr = config.getString("player-stats-gui.playtime-info.lore.total-playtime")
                .replace("{total_playtime}", Utils.ticksToFormattedPlaytime(totalPlaytime));
        lore.add(Utils.parseColors(totalPlaytimeStr));

        if (artificialPlaytime > 0) {
            String realPlaytimeStr = config.getString("player-stats-gui.playtime-info.lore.real-playtime")
                    .replace("{real_playtime}", Utils.ticksToFormattedPlaytime(realPlaytime));
            lore.add(Utils.parseColors(realPlaytimeStr));

            String artificialPlaytimeStr = config.getString("player-stats-gui.playtime-info.lore.artificial-playtime")
                    .replace("{artificial_playtime}", Utils.ticksToFormattedPlaytime(artificialPlaytime));
            lore.add(Utils.parseColors(artificialPlaytimeStr));
        }

        inv.setItem(PLAYTIME_INFO_SLOT, createGuiItem(
                Material.CLOCK,
                Utils.parseColors(config.getString("player-stats-gui.playtime-info.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(PLAYTIME_INFO_SLOT);
    }

    private void createFirstJoinInfoItem() {
        LocalDateTime firstJoin = subject.getFirstJoin();
        List<Component> lore = new ArrayList<>();

        if (firstJoin != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));

            String dateStr = config.getString("player-stats-gui.first-join-info.lore.date")
                    .replace("{date}", firstJoin.format(formatter));
            lore.add(Utils.parseColors(dateStr));

            Duration accountAge = Duration.between(firstJoin, LocalDateTime.now());
            String ageStr = config.getString("player-stats-gui.first-join-info.lore.account-age")
                    .replace("{age}", Utils.ticksToFormattedPlaytime(accountAge.getSeconds() * 20));
            lore.add(Utils.parseColors(ageStr));
        } else {
            lore.add(Utils.parseColors(config.getString("player-stats-gui.first-join-info.lore.no-data")));
        }

        inv.setItem(FIRST_JOIN_INFO_SLOT, createGuiItem(
                Material.SPAWNER,
                Utils.parseColors(config.getString("player-stats-gui.first-join-info.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(FIRST_JOIN_INFO_SLOT);
    }

    private void createLastSeenInfoItem() {
        LocalDateTime lastSeen = subject.getLastSeen();
        List<Component> lore = new ArrayList<>();

        if (lastSeen != null && !lastSeen.equals(LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getString("datetime-format"));

            String dateStr = config.getString("player-stats-gui.last-seen-info.lore.date")
                    .replace("{date}", lastSeen.format(formatter));
            lore.add(Utils.parseColors(dateStr));

            Duration timeSinceLastSeen = Duration.between(lastSeen, LocalDateTime.now());
            String elapsedStr = config.getString("player-stats-gui.last-seen-info.lore.time-elapsed")
                    .replace("{elapsed}", Utils.ticksToFormattedPlaytime(timeSinceLastSeen.getSeconds() * 20));
            lore.add(Utils.parseColors(elapsedStr));
        } else {
            lore.add(Utils.parseColors(config.getString("player-stats-gui.last-seen-info.lore.currently-online")));
        }

        inv.setItem(LAST_SEEN_INFO_SLOT, createGuiItem(
                Material.COMPASS,
                Utils.parseColors(config.getString("player-stats-gui.last-seen-info.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(LAST_SEEN_INFO_SLOT);
    }

    private void createJoinStreakInfoItem() {
        int relativeJoinStreak = subject.getRelativeJoinStreak();
        int absoluteJoinStreak = subject.getAbsoluteJoinStreak();

        List<Component> lore = new ArrayList<>();

        String relativeStr = config.getString("player-stats-gui.join-streak-info.lore.relative-streak")
                .replace("{relative_streak}", String.valueOf(relativeJoinStreak));
        lore.add(Utils.parseColors(relativeStr));

        String absoluteStr = config.getString("player-stats-gui.join-streak-info.lore.absolute-streak")
                .replace("{absolute_streak}", String.valueOf(absoluteJoinStreak));
        lore.add(Utils.parseColors(absoluteStr));

        inv.setItem(JOIN_STREAK_INFO_SLOT, createGuiItem(
                Material.FIRE_CHARGE,
                Utils.parseColors(config.getString("player-stats-gui.join-streak-info.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(JOIN_STREAK_INFO_SLOT);
    }

    private void createGoalsInfoItem() {
        ArrayList<String> completedGoals = subject.getCompletedGoals();
        List<Component> lore = new ArrayList<>();

        if (completedGoals.isEmpty()) {
            lore.add(Utils.parseColors(config.getString("player-stats-gui.goals-info.lore.no-goals")));
        } else {
            String countStr = config.getString("player-stats-gui.goals-info.lore.goals-count")
                    .replace("{count}", String.valueOf(completedGoals.size()));
            lore.add(Utils.parseColors(countStr));

            lore.add(Utils.parseColors(config.getString("player-stats-gui.goals-info.lore.separator")));

            // Show first few goals, or all if there are few
            int maxDisplayGoals = Math.min(completedGoals.size(), 5);
            int count = 0;
            for (String goal : completedGoals) {
                if (count >= maxDisplayGoals) break;
                String goalStr = config.getString("player-stats-gui.goals-info.lore.goal-item")
                        .replace("{goal}", goal);
                lore.add(Utils.parseColors(goalStr));
                count++;
            }

            if (completedGoals.size() > maxDisplayGoals) {
                String moreStr = config.getString("player-stats-gui.goals-info.lore.more-goals")
                        .replace("{remaining}", String.valueOf(completedGoals.size() - maxDisplayGoals));
                lore.add(Utils.parseColors(moreStr));
            }
        }

        inv.setItem(GOALS_INFO_SLOT, createGuiItem(
                Material.NETHER_STAR,
                Utils.parseColors(config.getString("player-stats-gui.goals-info.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(GOALS_INFO_SLOT);
    }

    private void createAccountOverviewItem() {
        List<Component> lore = new ArrayList<>();

        String playerStr = config.getString("player-stats-gui.account-overview.lore.player-name")
                .replace("{player}", subject.getNickname());
        lore.add(Utils.parseColors(playerStr));

        String uuidStr = config.getString("player-stats-gui.account-overview.lore.uuid")
                .replace("{uuid}", subject.getUuid().toString());
        lore.add(Utils.parseColors(uuidStr));

        // Add status (online/offline)
        boolean isOnline = Bukkit.getPlayer(subject.getUuid()) != null;
        String statusStr = config.getString("player-stats-gui.account-overview.lore.status")
                .replace("{status}", isOnline ?
                        config.getString("player-stats-gui.account-overview.lore.online") :
                        config.getString("player-stats-gui.account-overview.lore.offline"));
        lore.add(Utils.parseColors(statusStr));

        inv.setItem(ACCOUNT_OVERVIEW_SLOT, createGuiItem(
                Material.PLAYER_HEAD,
                Utils.parseColors(config.getString("player-stats-gui.account-overview.name")),
                lore.toArray(new Component[0])
        ));
        protectedSlots.add(ACCOUNT_OVERVIEW_SLOT);
    }

    private void createRefreshButton() {
        inv.setItem(REFRESH_BUTTON_SLOT, createGuiItem(
                Material.LIME_DYE,
                Utils.parseColors(config.getString("player-stats-gui.refresh-button.name")),
                Utils.parseColors(config.getString("player-stats-gui.refresh-button.lore"))
        ));
        protectedSlots.add(REFRESH_BUTTON_SLOT);
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

        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        // Handle refresh button
        if (slot == REFRESH_BUTTON_SLOT) {
            refreshStats();
            return;
        }

        // All other slots are informational only
        whoClicked.playSound(whoClicked.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    private void refreshStats() {
        if (!plugin.getSessionManager().validateSession(sender.getUniqueId(), sessionToken)) {
            plugin.getLogger().warning("Player " + sender.getName() + " attempted GUI action with invalid session token!");
            sender.closeInventory();
            return;
        }

        // Refresh the subject data from database
        subject = dbUsersManager.getUserFromUUID(subject.getUuid());

        if (subject == null) {
            sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                    config.getString("player-stats-gui.messages.player-not-found")));
            sender.closeInventory();
            return;
        }

        // Rebuild the GUI with fresh data
        initializeItems();
        sender.updateInventory();

        sender.sendMessage(Utils.parseColors(plugin.getConfiguration().getString("prefix") + " " +
                config.getString("player-stats-gui.messages.stats-refreshed")));
        sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    // Static method to clean up when GUI is closed
    public static void cleanup(UUID playerUuid) {
        activeGuis.remove(playerUuid);
    }
}