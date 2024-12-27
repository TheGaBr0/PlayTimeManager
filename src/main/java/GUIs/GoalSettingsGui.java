package GUIs;

import Goals.Goal;
import me.thegabro.playtimemanager.PlayTimeManager;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class GoalSettingsGui implements InventoryHolder, Listener {
    private Inventory inv;
    private Goal goal;
    private Object previousGui;
    private final ArrayList<Integer> protectedSlots = new ArrayList<>();

    public GoalSettingsGui(){}

    public GoalSettingsGui(Goal g, Object previousGui) {
        this.goal = g;
        this.previousGui = previousGui;
        this.inv = Bukkit.createInventory(this, 27, Component.text(goal.getName() + " - Settings"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void initializeItems() {
        inv.clear();

        // Fill background with glass panes
        for(int i = 0; i < 27; i++) {
            if(i != 10 && i != 12 && i != 14 && i != 16 && i != 22 && i != 26) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Component.text("§f[§6P.T.M.§f]§7")));
                protectedSlots.add(i);
            }
        }

        // Time setting
        inv.setItem(10, createGuiItem(Material.CLOCK,
                Component.text("§e§lRequired Time: §6" + convertTime(goal.getTime())),
                Component.text("§7Click to modify the required playtime")));

        // LuckPerms group setting
        inv.setItem(12, createGuiItem(Material.NAME_TAG,
                Component.text("§e§lGroup: §6" + goal.getLPGroup()),
                Component.text("§7Click to change the LuckPerms group")));

        // Goal message setting
        inv.setItem(14, createGuiItem(Material.OAK_SIGN,
                Component.text("§e§lGoal Message"),
                Component.text("§7Current: §f" + goal.getGoalMessage()),
                Component.text("§7Click to modify the message")));

        // Goal sound setting
        inv.setItem(16, createGuiItem(Material.NOTE_BLOCK,
                Component.text("§e§lGoal Sound"),
                Component.text("§7Current: §f" + goal.getGoalSound()),
                Component.text("§7Click to change the sound")));

        // Delete goal button
        inv.setItem(22, createGuiItem(Material.BARRIER,
                Component.text("§c§lDelete Goal"),
                Component.text("§7Click to delete this goal")));

        // Back button
        inv.setItem(26, createGuiItem(Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("§e§lBack")));


    }

    private ItemStack createGuiItem(Material material, @Nullable TextComponent name, @Nullable TextComponent... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        ArrayList<Component> metalore = new ArrayList<Component>(Arrays.asList(lore));
        meta.lore(metalore);
        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player p) {
        initializeItems();
        p.openInventory(inv);
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action) {
        if(clickedItem == null || clickedItem.getType().equals(Material.AIR)) {
            return;
        }

        if(!protectedSlots.contains(slot)){
            AllGoalsGui gui;

            switch(slot) {
                case 10: // Time setting
                    // Start chat input for new time
                    whoClicked.closeInventory();
                    // You would implement chat listener logic here
                    break;

                case 12: // LuckPerms group
                    // Start chat input for new group
                    whoClicked.closeInventory();
                    break;

                case 14: // Goal message
                    // Start chat input for new message
                    whoClicked.closeInventory();
                    break;

                case 16: // Goal sound
                    // Start chat input for new sound
                    whoClicked.closeInventory();
                    break;

                case 22: // Delete goal
                    if(previousGui != null) {
                        goal.kill();
                        whoClicked.closeInventory();
                        gui = (AllGoalsGui) previousGui;
                        gui.openInventory(whoClicked);
                    }
                    break;

                case 26: // Back button
                    if(previousGui != null) {
                        whoClicked.closeInventory();
                        gui = (AllGoalsGui) previousGui;
                        gui.openInventory(whoClicked);
                    }
                    break;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(e.getInventory().getHolder() instanceof GoalSettingsGui) {
            if((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);

                GoalSettingsGui gui = (GoalSettingsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction());
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