package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JoinStreakPermissionsGui implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 54;
    private static final int PERMISSIONS_PER_PAGE = 45;

    private Inventory inventory;
    private JoinStreakReward reward;
    private JoinStreakRewardSettingsGui parentGui;
    private int currentPage;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int PREV_PAGE = 45;
        static final int NEXT_PAGE = 53;
        static final int ADD_PERMISSION = 48;
        static final int BACK = 49;
        static final int DELETE_ALL = 50;
    }

    public JoinStreakPermissionsGui(){}

    public JoinStreakPermissionsGui(JoinStreakReward reward, JoinStreakRewardSettingsGui parentGui) {
        this.reward = reward;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Utils.parseColors("&6Permissions Editor"));
        this.currentPage = 0;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void openInventory(Player player) {
        initializeItems();
        player.openInventory(inventory);
    }

    private void initializeItems() {

        // Fill background
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, createBackgroundItem());
        }

        updatePermissionsPage();
        addControlButtons();
    }

    private ItemStack createBackgroundItem() {
        return parentGui.createGuiItem(
                Material.BLACK_STAINED_GLASS_PANE,
                Utils.parseColors("&f")
        );
    }

    private void updatePermissionsPage() {
        List<String> permissions = reward.getPermissions();
        int startIndex = currentPage * PERMISSIONS_PER_PAGE;

        // Clear previous items
        for (int i = 0; i < PERMISSIONS_PER_PAGE; i++) {
            inventory.setItem(i, createBackgroundItem());
        }

        // Display permissions
        for (int i = 0; i < PERMISSIONS_PER_PAGE && (startIndex + i) < permissions.size(); i++) {
            String permission = permissions.get(startIndex + i);

            if (permission.startsWith("group.")) {
                String groupName = permission.substring(6);
                boolean groupExists = PlayTimeManager.getInstance().isPermissionsManagerConfigured() &&
                        PlayTimeManager.getInstance().getLuckPermsApi() != null &&
                        me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager.getInstance(PlayTimeManager.getInstance()).groupExists(groupName);


                if (!groupExists) {
                    inventory.setItem(i, parentGui.createGuiItem(
                            Material.BOOK,
                            Utils.parseColors("&e" + permission),
                            Utils.parseColors("&7Click to edit"),
                            Utils.parseColors("&cRight-click to remove"),
                            Utils.parseColors(""),
                            Utils.parseColors("&c⚠ WARNING: Group '" + groupName + "' does not exist!"),
                            Utils.parseColors("&cPlease create this group in LuckPerms"),
                            Utils.parseColors("&cor remove this permission.")
                    ));
                } else {
                    inventory.setItem(i, parentGui.createGuiItem(
                            Material.BOOK,
                            Utils.parseColors("&e" + permission),
                            Utils.parseColors("&7Click to edit"),
                            Utils.parseColors("&cRight-click to remove")
                    ));
                }
            } else {
                inventory.setItem(i, parentGui.createGuiItem(
                        Material.PAPER,
                        Utils.parseColors("&e" + permission),
                        Utils.parseColors("&7Click to edit"),
                        Utils.parseColors("&cRight-click to remove")
                ));
            }
        }

        addControlButtons();
    }

    private void addControlButtons() {
        // Navigation buttons
        if (currentPage > 0) {
            inventory.setItem(Slots.PREV_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Utils.parseColors("&ePrevious Page")
            ));
        }

        if ((currentPage + 1) * PERMISSIONS_PER_PAGE < reward.getPermissions().size()) {
            inventory.setItem(Slots.NEXT_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Utils.parseColors("&eNext Page")
            ));
        }

        // Add new permission button
        inventory.setItem(Slots.ADD_PERMISSION, parentGui.createGuiItem(
                Material.EMERALD,
                Utils.parseColors("&a&lAdd Permission"),
                Utils.parseColors("&7Click to add a new permission")
        ));

        // Save and exit button
        inventory.setItem(Slots.BACK, parentGui.createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Utils.parseColors("&6&lBack")
        ));

        // Cancel button
        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete all"),
                Utils.parseColors("&7Click to discard every permission")
        ));
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        switch (slot) {
            case Slots.PREV_PAGE -> {
                if (currentPage > 0) {
                    currentPage--;
                    updatePermissionsPage();
                }
            }
            case Slots.NEXT_PAGE -> {
                if ((currentPage + 1) * PERMISSIONS_PER_PAGE < reward.getPermissions().size()) {
                    currentPage++;
                    updatePermissionsPage();
                }
            }
            case Slots.ADD_PERMISSION -> {
                whoClicked.closeInventory();
                openAddPermissionDialog(whoClicked);
            }
            case Slots.BACK -> {
                whoClicked.closeInventory();
                parentGui.openInventory(whoClicked);
            }
            case Slots.DELETE_ALL -> {
                handleDeleteAll(whoClicked);
            }
            default -> {
                if (slot < PERMISSIONS_PER_PAGE && (clickedItem.getType() == Material.PAPER || clickedItem.getType() == Material.BOOK)) {
                    String permission = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName()).substring(2); // Remove color codes
                    if (action.equals(InventoryAction.PICKUP_HALF)) {
                        // Remove permission (right-click)
                        reward.removePermission(permission);
                        updatePermissionsPage();
                    } else {
                        // Edit permission (left-click)
                        whoClicked.closeInventory();
                        openEditPermissionDialog(whoClicked, permission);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof JoinStreakPermissionsGui) {
            if ((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);

                JoinStreakPermissionsGui gui = (JoinStreakPermissionsGui) e.getInventory().getHolder();
                gui.onGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction());
            } else {
                if (e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                    e.setCancelled(true);
                }
            }
        }
    }


    private void openAddPermissionDialog(Player player) {
        Component header = Utils.parseColors("&e&l➕ Add Permission");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a new permission node:\n" +
                        "&7• Standard permission or 'group.groupname'\n" +
                        "&7• Type &c&ocancel&7 to exit"
        );

        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(divider);

        player.closeInventory();
        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("cancel")) {
                if(!message.isEmpty()){
                    reward.addPermission(message);
                    player.sendMessage(Utils.parseColors("&aPermission added: &f" + message));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cPermission addition cancelled"));
            }

            openInventory(player);
        });
    }

    private void openEditPermissionDialog(Player player, String oldPermission) {
        Component header = Utils.parseColors("&6&l✎ Edit Permission");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fCurrent Permission: &7" + oldPermission + "\n" +
                        "&7Enter a new permission node:\n" +
                        "&7• Type the new permission\n" +
                        "&7• Type &c&ocancel&7 to exit"
        );

        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(divider);

        player.closeInventory();
        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("cancel")) {
                if(!message.isEmpty()){
                    reward.removePermission(oldPermission);
                    reward.addPermission(message);
                }
                player.sendMessage(Utils.parseColors("&aPermission updated from &f" + oldPermission + " &ato &f" + message));
            } else {
                player.sendMessage(Utils.parseColors("&cPermission edit cancelled"));
            }

            openInventory(player);
        });
    }

    private void handleDeleteAll(Player whoClicked) {
        ItemStack warningItem = parentGui.createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete All Permissions"),
                Utils.parseColors("&7This will remove all permissions from this reward")
        );

        ConfirmationGui confirmationGui = new ConfirmationGui(warningItem, (confirmed) -> {
            if (confirmed) {
                for(String p: new ArrayList<>(reward.getPermissions())) {
                    reward.removePermission(p);
                }
                openInventory(whoClicked);
            } else {
                // Reopen permissions GUI if they clicked no
                openInventory(whoClicked);
            }
        });

        whoClicked.closeInventory();
        confirmationGui.openInventory(whoClicked);
    }
}
