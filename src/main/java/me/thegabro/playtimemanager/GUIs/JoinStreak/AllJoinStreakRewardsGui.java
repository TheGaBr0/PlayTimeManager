package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static me.thegabro.playtimemanager.GUIs.JoinStreak.RewardsInfoGui.activeGuis;

public class AllJoinStreakRewardsGui implements InventoryHolder, Listener {

    private final Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final JoinStreaksManager rewardsManager = JoinStreaksManager.getInstance();

    // Pagination variables
    private int currentPage = 0;
    private List<JoinStreakReward> sortedRewards = new ArrayList<>();
    private final int REWARDS_PER_PAGE = 28; // Maximum number of rewards per page
    private final int NEXT_BUTTON_SLOT = 50;
    private final int PREV_BUTTON_SLOT = 48;
    private final int PAGE_INDICATOR_SLOT = 49;
    private final int TOGGLE_SCHEDULE = 5;
    private final int CREATE_REWARD = 4;
    private final int INFO = 3;



    public AllJoinStreakRewardsGui() {
        inv = Bukkit.createInventory(this, 54, Component.text("Join Streak Rewards"));
    }

    public void openInventory(Player p) {
        currentPage = 0; // Reset to first page when opening the GUI
        initializeItems();
        p.openInventory(inv);
    }

    public void openInventory(Player p, int page) {
        currentPage = page;
        initializeItems();
        p.openInventory(inv);
    }

    public void initializeItems() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(plugin.getConfiguration().getDateTimeFormat());

        Map<String, Object> nextSchedule = rewardsManager.getNextSchedule();
        int leftIndex = 9;
        int rightIndex = 17;


        //This approach sorts primarily by minimum required joins, and for rewards that have the same minimum value,
        // it then sorts by the maximum required joins.
        Set<JoinStreakReward> rewardsSet = rewardsManager.getRewards();
        sortedRewards = rewardsSet.stream()
                .sorted(
                        Comparator.comparing(
                                (JoinStreakReward r) -> r.getMinRequiredJoins() == -1 ? Integer.MAX_VALUE : r.getMinRequiredJoins()
                        ).thenComparing(
                                r -> r.getMaxRequiredJoins() == -1 ? Integer.MAX_VALUE : r.getMaxRequiredJoins()
                        )
                )
                .toList();

        protectedSlots.clear();
        inv.clear();

        // Create GUI borders
        for(int i = 0; i < 54; i++) {
            if(i <= 9 || i >= 45 || i == leftIndex || i == rightIndex) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Utils.parseColors("&f[&6P.T.M.&f]&7")));
                protectedSlots.add(i);
                if(i == leftIndex) leftIndex += 9;
                if(i == rightIndex) rightIndex += 9;
            }
        }

        inv.setItem(INFO, createGuiItem(
                Material.COMPASS,
                Utils.parseColors("&e&lSystem Information"),
                Utils.parseColors("&7Next join streak check: &e" +
                        (nextSchedule.get("nextReset") != null ?
                                formatter.format(
                                        ((Date)nextSchedule.get("nextReset")).toInstant()
                                                .atZone(ZoneId.systemDefault())
                                                .toLocalDateTime()
                                ) + " &7(in &e" + nextSchedule.get("timeRemaining") + "&7)"
                                : "-")
                ),
                Utils.parseColors(""),
                Utils.parseColors("&7Reward cycle will reset after player"),
                Utils.parseColors("&7reaches reward with ID: &e#" +
                        (rewardsManager.getLastRewardByJoins() != null ? rewardsManager.getLastRewardByJoins().getId() : "-")),
                Utils.parseColors("&7which requires &e"+
                        (rewardsManager.getLastRewardByJoins() != null ? rewardsManager.getLastRewardByJoins().getMaxRequiredJoins() : "-")
                        +" &7consecutive joins to complete")
        ));
        protectedSlots.add(CREATE_REWARD);

        inv.setItem(CREATE_REWARD, createGuiItem(
                Material.EMERALD,
                Utils.parseColors("&a&lCreate New Reward"),
                Utils.parseColors("&7Click to create a new join streak reward")
        ));
        protectedSlots.add(CREATE_REWARD);

        boolean isActive = plugin.getConfiguration().getRewardsCheckScheduleActivation();
        boolean hasRewards = !rewardsManager.getRewards().isEmpty();

        inv.setItem(TOGGLE_SCHEDULE, createGuiItem(
                (isActive && hasRewards) ? Material.GREEN_CONCRETE : Material.RED_CONCRETE,
                Utils.parseColors((isActive && hasRewards) ? "&e&lRewards status: &2&lON" : "&e&lRewards status: &4&lOFF"),
                Utils.parseColors("&7Click to toggle the schedule"),
                Utils.parseColors("&7When set to &cOFF&7, the plugin will stop"),
                Utils.parseColors("&7granting rewards but will continue"),
                Utils.parseColors("&7tracking players' join streaks."),
                hasRewards ? Component.text("") : Utils.parseColors("&c&lNo rewards available!")
        ));
        protectedSlots.add(TOGGLE_SCHEDULE);

        // Add pagination controls if needed
        int totalPages = (int) Math.ceil((double) sortedRewards.size() / REWARDS_PER_PAGE);

        if (totalPages > 1) {
            // Page indicator
            inv.setItem(PAGE_INDICATOR_SLOT, createGuiItem(
                    Material.PAPER,
                    Utils.parseColors("&e&lPage " + (currentPage + 1) + " of " + totalPages)
            ));
            protectedSlots.add(PAGE_INDICATOR_SLOT);

            // Next page button
            if (currentPage < totalPages - 1) {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(
                        Material.ARROW,
                        Utils.parseColors("&a&lNext Page →"),
                        Utils.parseColors("&7Click to view the next page")
                ));
            } else {
                inv.setItem(NEXT_BUTTON_SLOT, createGuiItem(
                        Material.BARRIER,
                        Utils.parseColors("&c&lNo More Pages")
                ));
            }
            protectedSlots.add(NEXT_BUTTON_SLOT);

            // Previous page button
            if (currentPage > 0) {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(
                        Material.ARROW,
                        Utils.parseColors("&a&l← Previous Page"),
                        Utils.parseColors("&7Click to view the previous page")
                ));
            } else {
                inv.setItem(PREV_BUTTON_SLOT, createGuiItem(
                        Material.BARRIER,
                        Utils.parseColors("&c&lFirst Page")
                ));
            }
            protectedSlots.add(PREV_BUTTON_SLOT);
        }

        if(!sortedRewards.isEmpty()) {
            // Calculate start and end indices for current page
            int startIndex = currentPage * REWARDS_PER_PAGE;
            int endIndex = Math.min(startIndex + REWARDS_PER_PAGE, sortedRewards.size());

            // Get subset of rewards for current page
            List<JoinStreakReward> currentPageRewards = sortedRewards.subList(startIndex, endIndex);

            int slot = 10; // Start at first available slot after top border
            for(JoinStreakReward reward : currentPageRewards) {
                // Find next available slot
                while(protectedSlots.contains(slot)) slot++;
                if(slot >= 45) break; // Stop before bottom border

                inv.setItem(slot, createGuiItem(
                        Material.valueOf(reward.getItemIcon()),
                        Utils.parseColors("&e&l#ID&r&e " + reward.getId()),
                        Utils.parseColors("&7Required Joins: &e" + (reward.getMinRequiredJoins() == -1 ? "-" : reward.getRequiredJoinsDisplay())),
                        Utils.parseColors("&e" + reward.getPermissions().size() + "&7 " +
                                (reward.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                        Utils.parseColors("&e" + reward.getCommands().size() + "&7 " +
                                (reward.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                        Utils.parseColors("&aMiddle click to clone this reward"),
                        Utils.parseColors(""),
                        Utils.parseColors("&c&oShift-Right Click to delete")
                ));
                slot++;
            }
        } else {
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Utils.parseColors("&l&cNo join streak rewards have been created!")
            ));
        }
    }

    private ItemStack createGuiItem(Material material, @Nullable Component name, @Nullable Component...lore) {
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

    private JoinStreakReward getLatestReward() {
        if (sortedRewards.isEmpty()) {
            return null;
        }
        return sortedRewards.get(sortedRewards.size() - 1);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, @NotNull InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        if (slot == TOGGLE_SCHEDULE && (clickedItem.getType() == Material.GREEN_CONCRETE || clickedItem.getType() == Material.RED_CONCRETE)) {
            boolean hasRewards = !rewardsManager.getRewards().isEmpty();
            if (!hasRewards) {
                whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &cCannot enable rewards: No rewards have been created!"));
                return;
            }

            boolean toggleSuccess = rewardsManager.toggleJoinStreakCheckSchedule(whoClicked);

            if (toggleSuccess) {
                initializeItems();
                whoClicked.updateInventory();
            }
            return;
        }

        if (slot == CREATE_REWARD && clickedItem.getType() == Material.EMERALD) {
            whoClicked.closeInventory();
            rewardsManager.addReward(new JoinStreakReward(plugin, rewardsManager.getNextRewardId(), -1));
            openInventory(whoClicked);
            return;
        }

        if (slot == NEXT_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            openInventory(whoClicked, currentPage + 1);
            return;
        }

        if (slot == PREV_BUTTON_SLOT && clickedItem.getType() == Material.ARROW) {
            openInventory(whoClicked, currentPage - 1);
            return;
        }

        if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().displayName() != null) {
            String displayName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

            if (displayName.startsWith("#ID ") || displayName.contains("#ID ")) {
                // Extract the reward ID from the display name
                String idPart = displayName.substring(displayName.indexOf("#ID ") + 4).trim();
                int id;
                try {
                    id = Integer.parseInt(idPart);

                    JoinStreakReward reward = rewardsManager.getReward(id);
                    if (reward == null) return;

                    // Check for middle-click to clone
                    if (event.getClick().isCreativeAction()) {
                        whoClicked.closeInventory();
                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &7Cloning reward &e" + id + "&7..."));

                        // Create a new reward with the next available ID
                        int newId = rewardsManager.getNextRewardId();
                        JoinStreakReward clonedReward = cloneReward(newId, reward);

                        // Add the cloned reward to manager
                        rewardsManager.addReward(clonedReward);

                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aSuccessfully &7cloned reward &e" + id + " &7to new reward &e" + newId));
                        openInventory(whoClicked);
                        return;
                    }

                    // Check for shift-right-click to delete
                    if (event.isShiftClick() && event.isRightClick()) {
                        whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &7Deleting reward &e" + id + "&7..."));
                        Bukkit.getScheduler().runTaskAsynchronously(PlayTimeManager.getInstance(), () -> {
                            reward.kill();

                            // Switch back to main thread for UI updates
                            Bukkit.getScheduler().runTask(PlayTimeManager.getInstance(), () -> {
                                whoClicked.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aSuccessfully &7deleted reward &e" + id));
                                openInventory(whoClicked);
                            });
                        });
                    } else if (!event.getClick().isCreativeAction()) { // Regular click - open settings GUI
                        whoClicked.closeInventory();
                        JoinStreakRewardSettingsGui settingsGui = new JoinStreakRewardSettingsGui(reward, this);
                        settingsGui.openInventory(whoClicked);
                    }

                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return;
                }
            }


        }
    }

    private JoinStreakReward cloneReward(int newId, JoinStreakReward reward) {
        JoinStreakReward clonedReward = new JoinStreakReward(plugin, newId, reward.getMinRequiredJoins());

        clonedReward.setRequiredJoinsRange(reward.getMinRequiredJoins(), reward.getMaxRequiredJoins());
        clonedReward.setItemIcon(reward.getItemIcon());
        clonedReward.setRewardDescription(reward.getRewardDescription());
        clonedReward.setRewardMessage(reward.getRewardMessage());
        clonedReward.setDescription(reward.getDescription());
        clonedReward.setRewardSound(reward.getRewardSound());

        for (String command : reward.getCommands()) {
            clonedReward.addCommand(command);
        }

        for (String permission : reward.getPermissions()) {
            clonedReward.addPermission(permission);
        }
        return clonedReward;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof AllJoinStreakRewardsGui) {
            if ((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);

                AllJoinStreakRewardsGui gui = (AllJoinStreakRewardsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e);

            } else {
                if (e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AllJoinStreakRewardsGui && event.getPlayer() instanceof Player player) {
            if(!plugin.getConfiguration().getRewardsCheckScheduleActivation()){
                event.getPlayer().sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &c&l⚠ WARNING &c&l⚠"));
                event.getPlayer().sendMessage(Utils.parseColors("&7The join streak rewards schedule is currently &4&lDISABLED&6!"));
                event.getPlayer().sendMessage(Utils.parseColors("&7Player join streaks will still be tracked, but &c&nno reward will be granted&r&7."));
            }
        }
    }
}