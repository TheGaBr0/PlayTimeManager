
package me.thegabro.playtimemanager.GUIs.Goals;

import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Events.ChatEventManager;
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

import java.util.*;

public class GoalRequirementsGui implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 54, ITEMS_PER_PAGE = 45;
    private Inventory inventory;
    private Goal goal;
    private GoalSettingsGui parentGui;
    private int currentPage;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int PREV_PAGE = 45, NEXT_PAGE = 53, ADD_ITEM = 48, BACK = 49, DELETE_ALL = 50;
    }

    public GoalRequirementsGui(){}

    public GoalRequirementsGui(Goal goal, GoalSettingsGui parentGui) {
        this.goal = goal;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Utils.parseColors("&6Requirements Editor"));
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
        for (int i = 0; i < GUI_SIZE; i++) inventory.setItem(i, createBackgroundItem());
        updateRequirementsPage();
        addControlButtons();
    }

    private ItemStack createBackgroundItem() {
        return parentGui.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Utils.parseColors("&f"));
    }

    private void updateRequirementsPage() {
        List<String> permissions = goal.getRequirements().getPermissions();
        List<String> placeholders = goal.getRequirements().getPlaceholderConditions();
        List<String> combined = new ArrayList<>();
        combined.addAll(permissions);
        combined.addAll(placeholders);

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = currentPage * ITEMS_PER_PAGE + i;
            if (index >= combined.size()) {
                inventory.setItem(i, createBackgroundItem());
                continue;
            }

            String item = combined.get(index);
            boolean isPermission = index < permissions.size();
            Material icon = isPermission ? (item.startsWith("group.") ? Material.BOOK : Material.PAPER) : Material.NAME_TAG;
            List<Component> lore = new ArrayList<>(List.of(Utils.parseColors("&7Click to edit"), Utils.parseColors("&cRight-click to remove")));

            if (isPermission && item.startsWith("group.")) {
                String group = item.substring(6);
                boolean exists = PlayTimeManager.getInstance().isPermissionsManagerConfigured()
                        && PlayTimeManager.getInstance().getLuckPermsApi() != null
                        && me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager
                        .getInstance(PlayTimeManager.getInstance()).groupExists(group);
                if (!exists) lore.addAll(List.of(Utils.parseColors(""), Utils.parseColors("&c⚠ Group '" + group + "' does not exist!"), Utils.parseColors("&cCheck LuckPerms or remove this.")));
            }

            inventory.setItem(i, parentGui.createGuiItem(icon, Utils.parseColors((isPermission ? "&e" : "&b") + item), lore.toArray(Component[]::new)));
        }

        addControlButtons();
    }

    private void addControlButtons() {
        if (currentPage > 0) inventory.setItem(Slots.PREV_PAGE, parentGui.createGuiItem(Material.ARROW, Utils.parseColors("&ePrevious Page")));

        int total = goal.getRequirements().getPermissions().size() + goal.getRequirements().getPlaceholderConditions().size();
        if ((currentPage + 1) * ITEMS_PER_PAGE < total) inventory.setItem(Slots.NEXT_PAGE, parentGui.createGuiItem(Material.ARROW, Utils.parseColors("&eNext Page")));

        inventory.setItem(Slots.ADD_ITEM, parentGui.createGuiItem(Material.EMERALD, Utils.parseColors("&a&lAdd Requirement"), Utils.parseColors("&7Click to add a new permission or placeholder")));
        inventory.setItem(Slots.BACK, parentGui.createGuiItem(Material.MAGENTA_GLAZED_TERRACOTTA, Utils.parseColors("&6&lBack")));
        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(Material.BARRIER, Utils.parseColors("&c&lDelete All"), Utils.parseColors("&7Removes all requirements")));
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        switch (slot) {
            case Slots.PREV_PAGE -> { if (currentPage > 0) { currentPage--; updateRequirementsPage(); } }
            case Slots.NEXT_PAGE -> {
                int total = goal.getRequirements().getPermissions().size() + goal.getRequirements().getPlaceholderConditions().size();
                if ((currentPage + 1) * ITEMS_PER_PAGE < total) { currentPage++; updateRequirementsPage(); }
            }
            case Slots.ADD_ITEM -> openAddDialog(whoClicked);
            case Slots.BACK -> { whoClicked.closeInventory(); parentGui.openInventory(whoClicked); }
            case Slots.DELETE_ALL -> handleDeleteAll(whoClicked);
            default -> {
                if (slot < ITEMS_PER_PAGE && clickedItem.getItemMeta() != null) {
                    String display = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
                    String item = display.substring(2);
                    int index = currentPage * ITEMS_PER_PAGE + slot;
                    boolean isPermission = index < goal.getRequirements().getPermissions().size();

                    if (action == InventoryAction.PICKUP_HALF) {
                        if (isPermission) goal.getRequirements().removePermission(item);
                        else goal.getRequirements().removePlaceholderCondition(item);
                        updateRequirementsPage();
                    } else {
                        whoClicked.closeInventory();
                        if (isPermission) openEditPermissionDialog(whoClicked, item);
                        else openEditPlaceholderDialog(whoClicked, item);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof GoalRequirementsGui gui) {
            if (e.getRawSlot() < e.getInventory().getSize()) {
                e.setCancelled(true);
                gui.onGUIClick((Player) e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction());
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
            }
        }
    }

    private void openAddDialog(Player player) {
        Component header = Utils.parseColors("&6&l➕ Add Requirement");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a new requirement:\n" +
                        "&7• For permissions: Enter the permission node\n" +
                        "&7• For placeholders: Enter the condition (contains '%')\n" +
                        "&7• Type &c&o[cancel]&7 to exit"
        );

        Component fullMessage = Utils.parseColors(
                header + "\n" +
                        divider + "\n\n" +
                        instructions + "\n" +
                        divider
        );

        player.closeInventory();
        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("[cancel]")) {
                if (message.contains("%")) {
                    goal.getRequirements().addPlaceholderCondition(message);
                    player.sendMessage(Utils.parseColors("&aPlaceholder condition added: &f" + message));
                } else {
                    goal.getRequirements().addPermission(message);
                    player.sendMessage(Utils.parseColors("&aPermission added: &f" + message));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cRequirement addition cancelled"));
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
                        "&7• Type &c&o[cancel]&7 to exit"
        );

        Component fullMessage = Utils.parseColors(
                header + "\n" +
                        divider + "\n\n" +
                        instructions + "\n" +
                        divider
        );

        player.closeInventory();
        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("[cancel]")) {
                goal.getRequirements().removePermission(oldPermission);
                goal.getRequirements().addPermission(message);
                player.sendMessage(Utils.parseColors("&aPermission updated from &f" + oldPermission + " &ato &f" + message));
            } else {
                player.sendMessage(Utils.parseColors("&cPermission edit cancelled"));
            }

            openInventory(player);
        });
    }

    private void openEditPlaceholderDialog(Player player, String oldPlaceholder) {
        Component header = Utils.parseColors("&6&l✎ Edit Placeholder");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fCurrent Placeholder: &7" + oldPlaceholder + "\n" +
                        "&7Enter a new placeholder condition:\n" +
                        "&7• Must contain '%'\n" +
                        "&7• Type &c&o[cancel]&7 to exit"
        );

        Component fullMessage = Utils.parseColors(
                header + "\n" +
                        divider + "\n\n" +
                        instructions + "\n" +
                        divider
        );

        player.closeInventory();
        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, message) -> {
            if (!message.equalsIgnoreCase("[cancel]")) {
                goal.getRequirements().removePlaceholderCondition(oldPlaceholder);
                goal.getRequirements().addPlaceholderCondition(message);
                player.sendMessage(Utils.parseColors("&aPlaceholder updated from &f" + oldPlaceholder + " &ato &f" + message));
            } else {
                player.sendMessage(Utils.parseColors("&cPlaceholder edit cancelled"));
            }

            openInventory(player);
        });
    }

    private void handleDeleteAll(Player player) {
        ItemStack warning = parentGui.createGuiItem(Material.BARRIER, Utils.parseColors("&c&lDelete All Requirements"), Utils.parseColors("&7Removes all permissions and placeholders"));
        new ConfirmationGui(warning, confirmed -> {
            if (confirmed) {
                goal.getRequirements().getPermissions().clear();
                goal.getRequirements().getPlaceholderConditions().clear();
            }
            initializeItems();
            player.updateInventory();
        }).openInventory(player);
    }
}