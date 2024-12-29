// GoalSettingsGui.java
package GUIs;

import Goals.Goal;
import me.thegabro.playtimemanager.PlayTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.checkerframework.checker.nullness.qual.Nullable;
import net.wesjd.anvilgui.AnvilGUI;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GUI for managing goal settings in the PlayTime Manager plugin.
 */
public class GoalSettingsGui implements InventoryHolder, Listener {
    // Constants
    private static final int GUI_SIZE = 27;

    // GUI components
    private Inventory inventory;
    private Goal goal;
    private Object previousGui;

    // Slot mappings for main menu
    private static final class Slots {
        static final int TIME_SETTING = 10;
        static final int PERMISSIONS = 12;
        static final int GOAL_MESSAGE = 14;
        static final int GOAL_SOUND = 16;
        static final int DELETE_GOAL = 22;
        static final int BACK_BUTTON = 26;
    }

    public GoalSettingsGui(){}

    public GoalSettingsGui(Goal goal, Object previousGui) {
        this.goal = goal;
        this.previousGui = previousGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text(goal.getName() + " - Settings"));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void initializeItems() {
        inventory.clear();
        initializeBackground();
        initializeButtons();
    }

    private void initializeBackground() {
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!isButtonSlot(i)) {
                inventory.setItem(i, createGuiItem(
                        Material.BLACK_STAINED_GLASS_PANE,
                        Component.text("§f[§6P.T.M.§f]§7")
                ));
            }
        }
    }

    private boolean isButtonSlot(int slot) {
        return slot == Slots.TIME_SETTING ||
                slot == Slots.PERMISSIONS ||
                slot == Slots.GOAL_MESSAGE ||
                slot == Slots.GOAL_SOUND ||
                slot == Slots.DELETE_GOAL ||
                slot == Slots.BACK_BUTTON;
    }

    private void initializeButtons() {
        // Time setting button
        inventory.setItem(Slots.TIME_SETTING, createGuiItem(
                Material.CLOCK,
                Component.text("§e§lRequired Time: §6" + formatTime(goal.getTime())),
                Component.text("§7Click to modify the required playtime")
        ));

        // Permissions button
        inventory.setItem(Slots.PERMISSIONS, createGuiItem(
                Material.NAME_TAG,
                Component.text("§e§lPermissions"),
                Component.text("§7Currently §e" + goal.getPermissions().size() + "§7 " +
                        (goal.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")),
                Component.text("§7Click to change the permissions")
        ));

        // Message button
        inventory.setItem(Slots.GOAL_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Component.text("§e§lGoal Message"),
                Component.text("§7Current: §f" + goal.getGoalMessage()),
                Component.text("§7Click to modify the message")
        ));

        // Sound button
        inventory.setItem(Slots.GOAL_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Component.text("§e§lGoal Sound"),
                Component.text("§7Current: §f" + goal.getGoalSound()),
                Component.text("§7Click to change the sound")
        ));

        // Delete button
        inventory.setItem(Slots.DELETE_GOAL, createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete Goal"),
                Component.text("§7Click to delete this goal")
        ));

        // Back button
        inventory.setItem(Slots.BACK_BUTTON, createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("§e§lBack")
        ));
    }

    protected ItemStack createGuiItem(Material material, @Nullable TextComponent name, @Nullable TextComponent... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);

        if (lore != null) {
            meta.lore(Arrays.asList(lore));
        }

        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player player) {
        initializeItems();
        player.openInventory(inventory);
    }

    public void onGUIClick(Player player, int slot, ItemStack clickedItem, InventoryAction action) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        switch (slot) {
            case Slots.TIME_SETTING:
                openTimeEditor(player);
                break;

            case Slots.PERMISSIONS:
                player.closeInventory();
                new PermissionsGui(goal, this).openInventory(player);
                break;

            case Slots.GOAL_MESSAGE:
                // TODO: Implement message editing
                player.closeInventory();
                break;

            case Slots.GOAL_SOUND:
                // TODO: Implement sound editing
                player.closeInventory();
                break;

            case Slots.DELETE_GOAL:
                handleDeleteGoal(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;
        }
    }

    private void handleDeleteGoal(Player player) {
        if (previousGui != null) {
            goal.kill();
            player.closeInventory();
            ((AllGoalsGui) previousGui).openInventory(player);
        }
    }

    private void handleBackButton(Player player) {
        if (previousGui != null) {
            player.closeInventory();
            ((AllGoalsGui) previousGui).openInventory(player);
        }
    }

    private void openTimeEditor(Player player) {
        player.closeInventory();
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String text = stateSnapshot.getText();
                    long time = parseTimeFormat(text);

                    if (time != Long.MAX_VALUE) {
                        goal.setTime(time);
                        reopenMainGui(player);
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    } else {
                        return Collections.singletonList(
                                AnvilGUI.ResponseAction.updateTitle("Invalid format!", true)
                        );
                    }
                })
                .text(formatTime(goal.getTime()))
                .title("Format: 1d, 2h, 3m, 4s")
                .plugin(PlayTimeManager.getPlugin(PlayTimeManager.class))
                .open(player);
    }

    private void reopenMainGui(Player player) {
        Bukkit.getScheduler().runTask(
                PlayTimeManager.getPlugin(PlayTimeManager.class),
                () -> openInventory(player)
        );
    }

    private long parseTimeFormat(String input) {
        input = input.replace(" ", "");
        String[] timeParts = input.split(",");
        long timeToTicks = 0;

        Map<String, Integer> formatCounts = new HashMap<>();

        for (String part : timeParts) {
            try {
                int time = Integer.parseInt(part.replaceAll("[^\\d.]", ""));
                String format = part.replaceAll("\\d", "");

                if (formatCounts.getOrDefault(format, 0) > 0) {
                    continue;
                }

                switch (format) {
                    case "d":
                        timeToTicks += time * 1728000L;
                        break;
                    case "h":
                        timeToTicks += time * 72000L;
                        break;
                    case "m":
                        timeToTicks += time * 1200L;
                        break;
                    case "s":
                        timeToTicks += time * 20L;
                        break;
                    default:
                        return Long.MAX_VALUE;
                }

                formatCounts.put(format, 1);
            } catch (NumberFormatException e) {
                return Long.MAX_VALUE;
            }
        }
        return timeToTicks;
    }

    private String formatTime(long ticks) {
        if (ticks == Long.MAX_VALUE) {
            return "None";
        }

        long seconds = ticks / 20;
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours) - TimeUnit.DAYS.toMinutes(days);
        long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.DAYS.toSeconds(days);

        StringBuilder time = new StringBuilder();
        if (days > 0) time.append(days).append("d, ");
        if (hours > 0) time.append(hours).append("h, ");
        if (minutes > 0) time.append(minutes).append("m, ");
        time.append(remainingSeconds).append("s");

        return time.toString();
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




}