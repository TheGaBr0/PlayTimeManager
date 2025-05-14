package me.thegabro.playtimemanager.GUIs.Goals;

import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.GUIs.JoinStreak.JoinStreakRewardSettingsGui;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class AllGoalsGui implements InventoryHolder, Listener {

    private final Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();
    private final PlayTimeManager plugin = PlayTimeManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();
    public AllGoalsGui() {
        inv = Bukkit.createInventory(this, 54, Component.text("Goals"));
    }

    public void openInventory(Player p) {
        initializeItems();
        p.openInventory(inv);
    }

    public void initializeItems() {
        int leftIndex = 9;
        int rightIndex = 17;

        List<Goal> sortedGoals = goalsManager.getGoals().stream()
                .sorted(Comparator.comparing(Goal::getName)) // Sort by name
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

        if(!sortedGoals.isEmpty()) {
            int slot = 0;
            for(Goal goal : sortedGoals) {
                // Find next available slot
                while(protectedSlots.contains(slot)) slot++;
                if(slot >= 54) break; // Stop if inventory is full

                // Create goal item
                ItemStack item = goal.isActive() ?  new ItemStack(Material.EXPERIENCE_BOTTLE) : new ItemStack(Material.RED_DYE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("§e" + goal.getName()));
                List<Component> lore = Arrays.asList(
                        Utils.parseColors("§7Required Time: " + Utils.ticksToFormattedPlaytime(goal.getRequirements().getTime())),
                        Utils.parseColors("§7Active: ")
                                .append(Component.text(goal.isActive() ? "true" : "false")
                                        .color(goal.isActive() ? TextColor.color(0x55FF55) : TextColor.color(0xFF5555)))
                                .decoration(TextDecoration.ITALIC, false),
                        Utils.parseColors("§e" + goal.getRewardPermissions().size() + "§7 " + (goal.getRewardPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                        Utils.parseColors("§e" + goal.getRewardCommands().size() + "§7 " + (goal.getRewardCommands().size() != 1 ? "commands loaded" : "command loaded")),
                        Utils.parseColors(""),
                        Utils.parseColors("&c&oShift-Right Click to delete")
                );
                meta.lore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            }
        } else {
            // Display message if no goals exist
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Utils.parseColors("§l§cNo goals have been set!"),
                    Utils.parseColors("§7Click here to create a new goal")
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

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, @NotNull InventoryAction action, InventoryClickEvent event) {

        String goalName = "";

        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        if(clickedItem.getType() == Material.BARRIER) {
            whoClicked.closeInventory();

            // Create the base message
            Component baseMessage = Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + """
                To create a goal use:
                
                /playtimegoal set §e<name> §7time:§e<time> §7[activate:§etrue§7|§efalse§7]
            """);

            // Create the command text
            String command = "/playtimegoal set goal1 time:1d,2h,3m,4s";

            // Create the surrounding text parts
            Component preText = Component.text("You can ")
                    .color(TextColor.color(170,170,170));  // Gray color

            Component clickableText = Component.text("[click here]")
                    .color(TextColor.color(255,170,0))  // Gold color
                    .decoration(TextDecoration.BOLD, true)
                    .clickEvent(ClickEvent.suggestCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete command")));

            Component fullMessage = Component.empty()
                    .append(baseMessage)
                    .append(Component.text("\n"))
                    .append(preText)  // Added "You can" part
                    .append(clickableText)
                    .append(Component.text(" to autocomplete the command")
                            .color(TextColor.color(170,170,170)));  // Gray color

            whoClicked.sendMessage(fullMessage);
            return;
        }

        if(clickedItem.getItemMeta().hasDisplayName()){
            goalName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName()).substring(2);;
            Goal g = goalsManager.getGoal(goalName);

            if (event.isShiftClick() && event.isRightClick()) {
                handleDeleteGoal(whoClicked, g);
            } else if (!event.getClick().isCreativeAction()) {
                whoClicked.closeInventory();
                GoalSettingsGui settingsGui = new GoalSettingsGui(g, this);
                settingsGui.openInventory(whoClicked);
            }
        }

    }

    private void handleDeleteGoal(Player player, Goal goal) {
        player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &7Deleting goal &e" + goal.getName() + "&7..."));
        Bukkit.getScheduler().runTaskAsynchronously(PlayTimeManager.getInstance(), () -> {
            goal.kill();

            // Switch back to main thread for UI updates
            Bukkit.getScheduler().runTask(PlayTimeManager.getInstance(), () -> {
                player.sendMessage(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() + " &aSuccessfully &7deleted goal &e" + goal.getName()));
                initializeItems();
                player.updateInventory();
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getInventory().getHolder() instanceof AllGoalsGui) {
            if((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);

                AllGoalsGui gui = (AllGoalsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);

            }else{
                if(e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)){
                    e.setCancelled(true);
                }
            }
        }
    }
}
