package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Utils;
import me.thegabro.playtimemanager.Events.ChatEventManager;
import net.kyori.adventure.text.Component;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JoinStreakRewardPrizesGui implements InventoryHolder, Listener {

    private static final int GUI_SIZE = 54, ITEMS_PER_PAGE = 45;
    private Inventory inventory;
    private JoinStreakReward reward;
    private JoinStreakRewardSettingsGui parentGui;
    private int currentPage;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int PREV_PAGE = 45, NEXT_PAGE = 53, ADD_PERMISSION = 47,
                ADD_COMMAND = 48, BACK = 50, DELETE_ALL = 51;
    }

    public JoinStreakRewardPrizesGui(){}

    public JoinStreakRewardPrizesGui(JoinStreakReward reward, JoinStreakRewardSettingsGui parentGui) {
        this.reward = reward;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Utils.parseColors("&6Prizes Editor"));
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
        updateRewardsPage();
        addControlButtons();
    }

    private ItemStack createBackgroundItem() {
        return parentGui.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, Utils.parseColors("&f"));
    }

    private void updateRewardsPage() {
        List<String> permissions = reward.getPermissions();
        List<String> commands = reward.getCommands();
        List<String> combined = new ArrayList<>();
        combined.addAll(permissions);
        combined.addAll(commands);

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = currentPage * ITEMS_PER_PAGE + i;
            if (index >= combined.size()) {
                inventory.setItem(i, createBackgroundItem());
                continue;
            }

            String item = combined.get(index);
            boolean isPermission = index < permissions.size();
            Material icon = isPermission ?
                    (item.startsWith("group.") ? Material.BOOK : Material.NAME_TAG) :
                    Material.COMMAND_BLOCK;

            List<Component> lore = new ArrayList<>(List.of(
                    Utils.parseColors("&7Click to edit"),
                    Utils.parseColors("&cRight-click to remove")));

            if (isPermission && item.startsWith("group.")) {
                String groupName = item.substring(6);
                boolean groupExists = PlayTimeManager.getInstance().isPermissionsManagerConfigured() &&
                        PlayTimeManager.getInstance().getLuckPermsApi() != null &&
                        me.thegabro.playtimemanager.ExternalPluginSupport.LuckPermsManager
                                .getInstance(PlayTimeManager.getInstance()).groupExists(groupName);

                if (!groupExists) {
                    lore.addAll(List.of(
                            Utils.parseColors(""),
                            Utils.parseColors("&c⚠ Group '" + groupName + "' does not exist!"),
                            Utils.parseColors("&cPlease create this group in LuckPerms"),
                            Utils.parseColors("&cor remove this permission.")
                    ));
                }
            }

            inventory.setItem(i, parentGui.createGuiItem(icon, Utils.parseColors((isPermission ? "&e" : "&b") + item), lore.toArray(Component[]::new)));
        }
    }

    private void addControlButtons() {
        // Navigation buttons
        if (currentPage > 0) {
            inventory.setItem(Slots.PREV_PAGE, parentGui.createGuiItem(Material.ARROW, Utils.parseColors("&ePrevious Page")));
        }

        int totalItems = reward.getPermissions().size() + reward.getCommands().size();
        if ((currentPage + 1) * ITEMS_PER_PAGE < totalItems) {
            inventory.setItem(Slots.NEXT_PAGE, parentGui.createGuiItem(Material.ARROW, Utils.parseColors("&eNext Page")));
        }

        // Add permission button
        inventory.setItem(Slots.ADD_PERMISSION, parentGui.createGuiItem(
                Material.NAME_TAG,
                Utils.parseColors("&e&lAdd Permission"),
                Utils.parseColors("&7Click to add a new permission")
        ));

        // Add command button
        inventory.setItem(Slots.ADD_COMMAND, parentGui.createGuiItem(
                Material.COMMAND_BLOCK,
                Utils.parseColors("&b&lAdd Command"),
                Utils.parseColors("&7Click to add a new command")
        ));

        // Back button
        inventory.setItem(Slots.BACK, parentGui.createGuiItem(Material.MAGENTA_GLAZED_TERRACOTTA, Utils.parseColors("&6&lBack")));

        // Delete all button
        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete All"),
                Utils.parseColors("&7Removes all permissions and commands")
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof JoinStreakRewardPrizesGui gui) {
            if (e.getRawSlot() < e.getInventory().getSize()) {
                e.setCancelled(true);
                gui.onGUIClick((Player) e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction(), e);
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
            }
        }
    }

    public void onGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action, InventoryClickEvent event) {
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        event.setCancelled(true);

        switch (slot) {
            case Slots.PREV_PAGE -> { if (currentPage > 0) { currentPage--; updateRewardsPage(); } }
            case Slots.NEXT_PAGE -> {
                int totalItems = reward.getPermissions().size() + reward.getCommands().size();
                if ((currentPage + 1) * ITEMS_PER_PAGE < totalItems) {
                    currentPage++;
                    updateRewardsPage();
                }
            }
            case Slots.ADD_PERMISSION -> {
                whoClicked.closeInventory();
                openAddPermissionDialog(whoClicked);
            }
            case Slots.ADD_COMMAND -> {
                whoClicked.closeInventory();
                openAddCommandDialog(whoClicked);
            }
            case Slots.BACK -> {
                whoClicked.closeInventory();
                parentGui.openInventory(whoClicked);
            }
            case Slots.DELETE_ALL -> handleDeleteAll(whoClicked);
            default -> {
                if (slot < ITEMS_PER_PAGE && clickedItem.getItemMeta() != null) {
                    String display = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

                    int index = currentPage * ITEMS_PER_PAGE + slot;
                    boolean isPermission = index < reward.getPermissions().size();

                    if (event.isShiftClick() && event.isRightClick()) {
                        if (isPermission) {
                            reward.removePermission(display);
                        } else {
                            reward.removeCommand(display);
                        }
                        updateRewardsPage();
                    } else if (!event.getClick().isCreativeAction()) {
                        whoClicked.closeInventory();
                        if (isPermission) {
                            openEditPermissionDialog(whoClicked, display);
                        } else {
                            openEditCommandDialog(whoClicked, display);
                        }
                    }
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
                        "&7• Type &c&ocancel&r&7 to exit"
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
                        "&7• Type &c&ocancel&r&7 to exit"
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

    private void openAddCommandDialog(Player player) {
        Component header = Utils.parseColors("&b&l➕ Add Command");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fEnter a new command for this join streak reward:\n" +
                        "&7• Commands must be valid and use '/' as a prefix\n" +
                        "&7• Available placeholders: PLAYER_NAME\n" +
                        "&7• Type &c&ocancel&r&7 to exit"
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

        chatEventManager.startCommandInput(
                player,
                (p, input) -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(Utils.parseColors("&7Command addition cancelled"));
                        openInventory(p);
                        return;
                    }
                    reward.addCommand(input);
                    p.sendMessage(Utils.parseColors("&7Command &b" + input + " &7added. It will be executed when a player reaches the required join streak &e"));
                    openInventory(p);
                }
        );
    }

    private void openEditCommandDialog(Player player, String oldCommand) {
        Component header = Utils.parseColors("&6&l✎ Edit Command");
        Component divider = Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Component instructions = Utils.parseColors(
                "&fCurrent Command: &7" + oldCommand + "\n" +
                        "&7Enter a new command:\n" +
                        "&7• Commands must be valid and use '/' as a prefix\n" +
                        "&7• Available placeholders: PLAYER_NAME\n" +
                        "&7• Type &c&ocancel&r&7 to exit"
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

        chatEventManager.startCommandInput(
                player,
                (p, input) -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(Utils.parseColors("&cCommand edit cancelled"));
                        openInventory(p);
                        return;
                    }

                    reward.removeCommand(oldCommand);
                    reward.addCommand(input);
                    p.sendMessage(Utils.parseColors("&aCommand edited successfully!"));
                    openInventory(p);
                }
        );

        // Add clickable message to auto-complete
        Component preText = Component.text("You can ")
                .color(TextColor.color(170,170,170));  // Gray color

        Component clickableText = Component.text("[click here]")
                .color(TextColor.color(255,170,0))  // Gold color
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(oldCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete command")));

        Component clickableMessage = Component.empty()
                .append(Component.text("\n"))
                .append(preText)
                .append(clickableText)
                .append(Component.text(" to autocomplete the old command")
                        .color(TextColor.color(170,170,170)));  // Gray color

        player.sendMessage(clickableMessage);
    }

    private void handleDeleteAll(Player whoClicked) {
        ItemStack warning = parentGui.createGuiItem(Material.BARRIER, Utils.parseColors("&c&lDelete All Rewards"), Utils.parseColors("&7This will remove all permissions and commands from this join streak reward"));
        new ConfirmationGui(warning, confirmed -> {
            if (confirmed) {
                // Remove all permissions
                for(String perm: new ArrayList<>(reward.getPermissions())) {
                    reward.removePermission(perm);
                }

                // Remove all commands
                for(String cmd: new ArrayList<>(reward.getCommands())) {
                    reward.removeCommand(cmd);
                }
            }
            initializeItems();
            whoClicked.updateInventory();
        }).openInventory(whoClicked);
    }
}