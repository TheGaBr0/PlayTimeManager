package GUIs;

import Goals.Goal;
import Goals.GoalManager;
import me.thegabro.playtimemanager.PlayTimeManager;
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

import Goals.GoalManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AllGoalsGui implements InventoryHolder, Listener {

    private final Inventory inv;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();

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
        Set<Goal> goals = GoalManager.getGoals();
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

        if(!goals.isEmpty()) {
            int slot = 0;
            for(Goal goal : goals) {
                // Find next available slot
                while(protectedSlots.contains(slot)) slot++;
                if(slot >= 54) break; // Stop if inventory is full

                // Create goal item
                inv.setItem(slot, createGuiItem(
                        Material.EXPERIENCE_BOTTLE,
                        Component.text("§e" + goal.getName()),
                        Component.text("§7Required Time: " + convertTime(goal.getTime())),
                        Component.text("§7Group: " + goal.getLPGroup()),
                        Component.text("§7Press §eright mouse button §7to delete")
                ));
                slot++;
            }
        } else {
            // Display message if no goals exist
            inv.setItem(22, createGuiItem(
                    Material.BARRIER,
                    Component.text("§l§cNo goals have been set!"),
                    Component.text("§7Use §e/playtimegoal set §7to add goals")
            ));
        }
    }

    private ItemStack createGuiItem(Material material, @Nullable TextComponent name, @Nullable TextComponent...lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        ArrayList<Component> metalore = new ArrayList<Component>(Arrays.asList(lore));

        meta.lore(metalore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, @NotNull InventoryAction action) {

        String goalName = "";

        if(clickedItem == null || clickedItem.getType().equals(Material.AIR))
            return;

        if(clickedItem.getType() == Material.BARRIER) {
            whoClicked.closeInventory();
            whoClicked.sendMessage(Component.text(
                    "[§6PlayTime§eManager§f]§7 To create a goal use: /playtimegoal set §e<name> §7setTime:§e<time> §7setLPGroup:§e<group>"));
            return;
        }

        if(!protectedSlots.contains(slot)) {
            if(clickedItem.getItemMeta().hasDisplayName())
                goalName =  PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

            if(action.equals(InventoryAction.PICKUP_HALF)){
                whoClicked.closeInventory();
                ConfirmationGui confirmationGui = new ConfirmationGui(GoalManager.getGoal(goalName.substring(2)), clickedItem, this);
                confirmationGui.openInventory(whoClicked);

            }else{
                whoClicked.closeInventory();
                GoalSettingsGui settingsGui = new GoalSettingsGui(GoalManager.getGoal(goalName.substring(2)), this);
                settingsGui.openInventory(whoClicked);

            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getInventory().getHolder() instanceof AllGoalsGui) {
            if((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);

                AllGoalsGui gui = (AllGoalsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction());

            }else{
                if(e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)){
                    e.setCancelled(true);
                }
            }
        }
    }

    private String convertTime(long secondsx) {

        if(secondsx == Long.MAX_VALUE)
            return "None";
        else
            secondsx/=20;

        int days = (int) TimeUnit.SECONDS.toDays(secondsx);
        int hours = (int) (TimeUnit.SECONDS.toHours(secondsx) - TimeUnit.DAYS.toHours(days));
        int minutes = (int) (TimeUnit.SECONDS.toMinutes(secondsx) - TimeUnit.HOURS.toMinutes(hours)
                - TimeUnit.DAYS.toMinutes(days));
        int seconds = (int) (TimeUnit.SECONDS.toSeconds(secondsx) - TimeUnit.MINUTES.toSeconds(minutes)
                - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days));

        if (days != 0) {
            return days + "d, " + hours + "h, " + minutes + "m, " + seconds + "s";
        } else {
            if (hours != 0) {
                return hours + "h, " + minutes + "m, " + seconds + "s";
            } else {
                if (minutes != 0) {
                    return minutes + "m, " + seconds + "s";
                } else {
                    return seconds + "s";
                }
            }

        }
    }
}
