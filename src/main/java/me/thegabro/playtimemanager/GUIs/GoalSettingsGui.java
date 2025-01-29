package me.thegabro.playtimemanager.GUIs;

import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
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

public class GoalSettingsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 45;
    private Inventory inventory;
    private Goal goal;
    private Object previousGui;
    private PlayTimeManager plugin;

    private static final class Slots {
        static final int TIME_SETTING = 10;
        static final int GOAL_PERMISSIONS = 12;
        static final int GOAL_MESSAGE = 14;
        static final int GOAL_SOUND = 16;
        static final int GOAL_ACTIVATION_STATUS = 29;
        static final int GOAL_COMMANDS = 31;
        static final int DELETE_GOAL = 33;
        static final int UNCOMPLETE_GOAL = 36;
        static final int BACK_BUTTON = 44;
    }

    public GoalSettingsGui(){}

    public GoalSettingsGui(Goal goal, Object previousGui) {
        this.goal = goal;
        this.previousGui = previousGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text(goal.getName() + " - Settings"));
        this.plugin = PlayTimeManager.getInstance();
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
                slot == Slots.GOAL_PERMISSIONS ||
                slot == Slots.GOAL_MESSAGE ||
                slot == Slots.GOAL_SOUND ||
                slot == Slots.GOAL_ACTIVATION_STATUS ||
                slot == Slots.GOAL_COMMANDS ||
                slot == Slots.DELETE_GOAL ||
                slot == Slots.BACK_BUTTON;
    }

    private void initializeButtons() {
        // Time setting button
        inventory.setItem(Slots.TIME_SETTING, createGuiItem(
                Material.CLOCK,
                Component.text("§e§lRequired Time: §6" + Utils.ticksToFormattedPlaytime(goal.getTime())),
                Component.text("§7Click to modify the required playtime")
        ));

        // Permissions button
        List<TextComponent> lore = new ArrayList<>();
        lore.add(Component.text("§7Currently §e" + goal.getPermissions().size() + "§7 " +
                (goal.getPermissions().size() != 1 ? "permissions loaded" : "permission loaded")));

        lore.add(Component.text("§7Click to change the permissions"));

        if (!PlayTimeManager.getInstance().isPermissionsManagerConfigured()) {
            lore.add(Component.text(""));
            lore.add(Component.text("§4§lWARNING: §cNo permissions plugin detected!"));
            lore.add(Component.text("§cPermissions will not be assigned"));
        }


        inventory.setItem(Slots.GOAL_PERMISSIONS, createGuiItem(
                Material.NAME_TAG,
                Component.text("§e§lPermissions"),
                lore.toArray(new TextComponent[0])
        ));

        // Message button
        inventory.setItem(Slots.GOAL_MESSAGE, createGuiItem(
                Material.OAK_SIGN,
                Component.text("§e§lGoal Message"),
                Component.text("§cTo update this setting, please edit the"),
                Component.text("§c'" + goal.getName() + ".yml' configuration file."),
                Component.text("§cModification via GUI is not currently supported.")
        ));

        // Sound button
        inventory.setItem(Slots.GOAL_SOUND, createGuiItem(
                Material.NOTE_BLOCK,
                Component.text("§e§lGoal Sound"),
                Component.text("§7Current: §f" + goal.getGoalSound()),
                Component.text("§cTo update this setting, please edit the"),
                Component.text("§c'" + goal.getName() + ".yml' configuration file."),
                Component.text("§cModification via GUI is not currently supported.")
        ));

        // Activation status button
        inventory.setItem(Slots.GOAL_ACTIVATION_STATUS, createGuiItem(
                goal.isActive() ? Material.GREEN_CONCRETE : Material.RED_CONCRETE,
                Component.text(goal.isActive() ? "§a§lGoal Active" : "§c§lGoal Inactive"),
                Component.text("§7Click to " + (goal.isActive() ? "deactivate" : "activate") + " this goal")
        ));

        // me.thegabro.playtimemanager.Commands button
        inventory.setItem(Slots.GOAL_COMMANDS, createGuiItem(
                Material.COMMAND_BLOCK,
                Component.text("§e§lCommands"),
                Component.text("§7Currently §e" + goal.getCommands().size() + "§7 " +
                        (goal.getCommands().size() != 1 ? "commands loaded" : "command loaded")),
                Component.text("§7Click to manage commands")
        ));

        // Delete button
        inventory.setItem(Slots.DELETE_GOAL, createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete Goal"),
                Component.text("§7Click to delete this goal")
        ));

        // Add uncomplete goal button
        inventory.setItem(Slots.UNCOMPLETE_GOAL, createGuiItem(
                Material.PLAYER_HEAD,
                Component.text("§e§lUncomplete Goal for Player"),
                Component.text("§7Click to remove this goal's completion"),
                Component.text("§7from a specific player")
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

            case Slots.GOAL_PERMISSIONS:
                player.closeInventory();
                new PermissionsGui(goal, this).openInventory(player);
                break;

            case Slots.GOAL_MESSAGE:
                // TODO: Implement message editing
                break;

            case Slots.GOAL_SOUND:
                // TODO: Implement sound editing
                break;

            case Slots.GOAL_COMMANDS:
                player.closeInventory();
                new CommandsGui(goal, this).openInventory(player);
                break;

            case Slots.GOAL_ACTIVATION_STATUS:
                goal.setActivation(!goal.isActive());
                initializeItems();
                break;

            case Slots.DELETE_GOAL:
                handleDeleteGoal(player);
                break;

            case Slots.UNCOMPLETE_GOAL:
                openUncompleteGoalDialog(player);
                break;

            case Slots.BACK_BUTTON:
                handleBackButton(player);
                break;
        }
    }

    private void handleDeleteGoal(Player player) {
        ItemStack goalItem = createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete Goal: " + goal.getName())
        );

        ConfirmationGui confirmationGui = new ConfirmationGui(goalItem, (confirmed) -> {
            if (confirmed) {
                goal.kill();
                if (previousGui != null) {
                    ((AllGoalsGui) previousGui).openInventory(player);
                }
            } else {
                openInventory(player);
            }
        });

        player.closeInventory();
        confirmationGui.openInventory(player);
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
                    long time = Utils.formattedPlaytimeToTicks(text);

                    if (time != -1L) {
                        goal.setTime(time);
                        reopenMainGui(player);
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    } else {
                        return Collections.singletonList(
                                AnvilGUI.ResponseAction.updateTitle("Invalid format!", true)
                        );
                    }
                })
                .onClose(state -> {
                    // Reopen the PermissionsGui when the anvil is closed
                    Bukkit.getScheduler().runTask(PlayTimeManager.getPlugin(PlayTimeManager.class), () -> openInventory(state.getPlayer()));
                })
                .text(Utils.ticksToFormattedPlaytime(goal.getTime()))
                .title("Format: 1y,2d,3h,4m,5s")
                .plugin(PlayTimeManager.getPlugin(PlayTimeManager.class))
                .open(player);
    }

    private void openUncompleteGoalDialog(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);

        player.closeInventory();
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String playerName = stateSnapshot.getText().replace(" ", "");

                    DBUser user = plugin.getDbUsersManager().getUserFromNickname(playerName);

                    if(user == null){
                        return Collections.singletonList(
                                AnvilGUI.ResponseAction.updateTitle("Player not found!", true)
                        );
                    }

                    if (user.hasCompletedGoal(goal.getName())) {
                        user.unmarkGoalAsCompleted(goal.getName());
                        player.sendMessage(Component.text("[§6PlayTime§eManager§f]§7 Successfully uncompleted goal §a" +
                                goal.getName() + "§7 for player §a" + playerName));

                        reopenMainGui(player);
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    } else {
                        return Collections.singletonList(
                                AnvilGUI.ResponseAction.updateTitle("Player hasn't reach that goal!", true)
                        );
                    }
                })
                .onClose(state -> {
                    Bukkit.getScheduler().runTask(PlayTimeManager.getPlugin(PlayTimeManager.class),
                            () -> openInventory(state.getPlayer()));
                })
                .itemLeft(item)
                .title("Enter player name:")
                .plugin(PlayTimeManager.getPlugin(PlayTimeManager.class))
                .open(player);
    }

    private void reopenMainGui(Player player) {
        Bukkit.getScheduler().runTask(
                PlayTimeManager.getPlugin(PlayTimeManager.class),
                () -> openInventory(player)
        );
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