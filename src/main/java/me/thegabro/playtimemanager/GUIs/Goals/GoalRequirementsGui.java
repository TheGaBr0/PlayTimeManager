package me.thegabro.playtimemanager.GUIs.Goals;

import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.Goals.Goal;
import me.thegabro.playtimemanager.PlaceholderConditionEvaluator;
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
    private PlaceholderConditionEvaluator placeholderConditionEvaluator = PlaceholderConditionEvaluator.getInstance();


    private static final class Slots {
        static final int PREV_PAGE = 45, TIME_SETTING = 46, NEXT_PAGE = 53, ADD_PERMISSION = 47,
                ADD_PLACEHOLDER = 48, BACK = 50, DELETE_ALL = 51;
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
            Material icon = isPermission ? (item.startsWith("group.") ? Material.BOOK : Material.NAME_TAG) : Material.ARMOR_STAND;
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

        inventory.setItem(Slots.TIME_SETTING, parentGui.createGuiItem(
                Material.CLOCK,
                Component.text("§e§lRequired Time: §6" + Utils.ticksToFormattedPlaytime(goal.getRequirements().getTime())),
                Component.text("§7Click to modify the required playtime")
        ));

        inventory.setItem(Slots.ADD_PERMISSION, parentGui.createGuiItem(Material.CLOCK, Utils.parseColors("&e&lC"),
                Utils.parseColors("&7Click to add a new permission node")));

        inventory.setItem(Slots.ADD_PERMISSION, parentGui.createGuiItem(Material.NAME_TAG, Utils.parseColors("&e&lAdd Permission"),
                Utils.parseColors("&7Click to add a new permission node")));

        inventory.setItem(Slots.ADD_PLACEHOLDER, parentGui.createGuiItem(Material.ARMOR_STAND, Utils.parseColors("&b&lAdd Placeholder"),
                Utils.parseColors("&7Click to add a new placeholder condition")));

        inventory.setItem(Slots.BACK, parentGui.createGuiItem(Material.MAGENTA_GLAZED_TERRACOTTA, Utils.parseColors("&6&lBack")));
        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(Material.BARRIER, Utils.parseColors("&c&lDelete All"), Utils.parseColors("&7Removes all requirements")));
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action, InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        event.setCancelled(true);
        switch (slot) {
            case Slots.PREV_PAGE -> { if (currentPage > 0) { currentPage--; updateRequirementsPage(); } }
            case Slots.NEXT_PAGE -> {
                int total = goal.getRequirements().getPermissions().size() + goal.getRequirements().getPlaceholderConditions().size();
                if ((currentPage + 1) * ITEMS_PER_PAGE < total) { currentPage++; updateRequirementsPage(); }
            }
            case Slots.TIME_SETTING -> openTimeEditor(whoClicked);
            case Slots.ADD_PERMISSION -> openAddPermissionDialog(whoClicked);
            case Slots.ADD_PLACEHOLDER -> openAddPlaceholderDialog(whoClicked);
            case Slots.BACK -> { whoClicked.closeInventory(); parentGui.openInventory(whoClicked); }
            case Slots.DELETE_ALL -> handleDeleteAll(whoClicked);
            default -> {
                if (slot < ITEMS_PER_PAGE && clickedItem.getItemMeta() != null) {
                    String display = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

                    int index = currentPage * ITEMS_PER_PAGE + slot;
                    boolean isPermission = index < goal.getRequirements().getPermissions().size();

                    if (event.isShiftClick() && event.isRightClick()) {
                        if (isPermission) goal.removeRequirementPermission(display);
                        else goal.removePlaceholderCondition(display);
                        updateRequirementsPage();
                    } else if (!event.getClick().isCreativeAction()) {
                        whoClicked.closeInventory();
                        if (isPermission) openEditPermissionDialog(whoClicked, display);
                        else openEditPlaceholderDialog(whoClicked, display);
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
                gui.onGUIClick((Player) e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
            }
        }
    }

    private void openTimeEditor(Player player) {
        player.closeInventory();

        // Header with goal name
        Component header = Utils.parseColors("&6&l✎ Time Editor: &e" + goal.getName());

        // Divider for visual separation
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Instructions with better spacing and formatting
        Component instructions = Utils.parseColors(
                "&fEnter the new time requirement for this goal.\n" +
                        "&7• Format: &e1y,2d,3h,4m,5s\n" +
                        "&7• Current value: &e" + Utils.ticksToFormattedPlaytime(goal.getRequirements().getTime()) + "\n" +
                        "&7• Type &c&ocancel&7 to exit"
        );

        // Create full message
        Component fullMessage = Component.empty()
                .append(header)
                .append(Component.newline())
                .append(divider)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(Component.newline())
                .append(divider);

        player.sendMessage(fullMessage);

        chatEventManager.startChatInput(player, (p, input) -> {
            if (!input.equalsIgnoreCase("cancel")) {
                long time = Utils.formattedPlaytimeToTicks(input);

                if (time != -1L) {
                    goal.setTime(time);
                    player.sendMessage(Utils.parseColors("&aGoal time requirement updated successfully!"));
                } else {
                    player.sendMessage(Utils.parseColors("&cInvalid time format! Please use format: 1y,2d,3h,4m,5s"));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cGoal time edit cancelled"));
            }
            openInventory(player);
        });

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
                    goal.addRequirementPermission(message);
                    player.sendMessage(Utils.parseColors("&aPermission added: &f" + message));
                }
            } else {
                player.sendMessage(Utils.parseColors("&cPermission addition cancelled"));
            }

            openInventory(player);
        });
    }

    private void openAddPlaceholderDialog(Player player) {
        Component header = Utils.parseColors("&b&l➕ Add Placeholder");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a new placeholder condition:\n" +
                        "&7• Must be a boolean expression (e.g. &f%PTM_joinstreak% >= 2&7)\n" +
                        "&7• Only expressions with 2 operands are accepted\n" +
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
                if(!message.isEmpty()) {
                    if (placeholderConditionEvaluator.isValid(player, message)) {
                        goal.addPlaceholderCondition(message);
                        player.sendMessage(Utils.parseColors("&aPlaceholder condition added: &f" + message));
                    } else {
                        player.sendMessage(Utils.parseColors("&cThis placeholder condition is not valid"));
                    }
                }
            } else {
                player.sendMessage(Utils.parseColors("&cPlaceholder addition cancelled"));
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
                    goal.removeRequirementPermission(oldPermission);
                    goal.addRequirementPermission(message);
                }
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
                        "&7• Must be a boolean expression (e.g. &f%PTM_joinstreak% >= 2&7)\n" +
                        "&7• Only expressions with 2 operands are accepted\n" +
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
                    if (placeholderConditionEvaluator.isValid(player, message)) {
                        goal.removePlaceholderCondition(oldPlaceholder);
                        goal.addPlaceholderCondition(message);
                        player.sendMessage(Utils.parseColors("&aPlaceholder updated from &f" + oldPlaceholder + " &ato &f" + message));
                    } else {
                        player.sendMessage(Utils.parseColors("&cThis placeholder condition is not valid"));
                    }
                }
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
                goal.clearPlaceholderConditions();
                goal.clearRequirementPermissions();
            }
            initializeItems();
            player.updateInventory();
        }).openInventory(player);
    }
}