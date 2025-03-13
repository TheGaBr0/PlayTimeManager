package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreaksManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Component.text("§f[§6P.T.M.§f]§7")));
                protectedSlots.add(i);
                if(i == leftIndex) leftIndex += 9;
                if(i == rightIndex) rightIndex += 9;
            }
        }

        inv.setItem(4, createGuiItem(
                Material.EMERALD,
                Component.text("§a§lCreate New Reward"),
                Component.text("§7Click to create a new join streak reward")
        ));
        protectedSlots.add(4);

        // Add pagination controls if needed
        int totalPages = (int) Math.ceil((double) sortedRewards.size() / REWARDS_PER_PAGE);

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
                        Component.text("§e§l#ID§r§e " + reward.getId()),
                        Component.text("§7Required Joins: §e" + (reward.getMinRequiredJoins() == -1 ? "-" : reward.getRequiredJoinsDisplay())),
                        Component.text("§e" + reward.getPermissions().size() + "§7 " +
                                (reward.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                        Component.text("§e" + reward.getCommands().size() + "§7 " +
                                (reward.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                        Component.text("§aMiddle click to clone this reward"),
                        Component.text(""),
                        Component.text("§c§oShift-Right Click to delete")
                ));
                slot++;
            }
        } else {
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Component.text("§l§cNo join streak rewards have been created!")
            ));
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

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, @NotNull InventoryAction action, @NotNull InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        if (slot == 4 && clickedItem.getType() == Material.EMERALD) {
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

        if (clickedItem.getItemMeta().hasDisplayName()) {
            String rewardID = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

            // Extract the reward ID from the display name
            int id;
            try {
                id = Integer.parseInt(rewardID.substring(12));
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return;
            }

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
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);

            } else {
                if (e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                    e.setCancelled(true);
                }
            }
        }
    }
}