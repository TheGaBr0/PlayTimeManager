package me.thegabro.playtimemanager.GUIs;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.Goals.Goal;
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

public class CommandsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 54;
    private static final int COMMANDS_PER_PAGE = 45;

    private Inventory inventory;
    private Goal goal;
    private GoalSettingsGui parentGui;
    private int currentPage = 0;
    private ChatEventManager chatHandler;

    private static final class Slots {
        static final int PREV_PAGE = 45;
        static final int NEXT_PAGE = 53;
        static final int ADD_COMMAND = 48;
        static final int BACK = 49;
        static final int DELETE_ALL = 50;
    }

    public CommandsGui(){}

    public CommandsGui(Goal goal, GoalSettingsGui parentGui) {
        this.goal = goal;
        this.parentGui = parentGui;
        this.chatHandler = new ChatEventManager();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Component.text("§6Commands Editor"));
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
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, createBackgroundItem());
        }
        updateCommandsPage();
        addControlButtons();
    }

    private ItemStack createBackgroundItem() {
        return parentGui.createGuiItem(
                Material.BLACK_STAINED_GLASS_PANE,
                Component.text("§f")
        );
    }

    private void updateCommandsPage() {
        List<String> commands = goal.getCommands();
        int startIndex = currentPage * COMMANDS_PER_PAGE;

        for (int i = 0; i < COMMANDS_PER_PAGE; i++) {
            inventory.setItem(i, createBackgroundItem());
        }

        for (int i = 0; i < COMMANDS_PER_PAGE && (startIndex + i) < commands.size(); i++) {
            String command = commands.get(startIndex + i);
            inventory.setItem(i, parentGui.createGuiItem(
                    Material.PAPER,
                    Component.text("§e" + command),
                    Component.text("§7Click to edit"),
                    Component.text("§cRight-click to remove")
            ));
        }

        addControlButtons();
    }

    private void addControlButtons() {
        if (currentPage > 0) {
            inventory.setItem(Slots.PREV_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Component.text("§ePrevious Page")
            ));
        }

        if ((currentPage + 1) * COMMANDS_PER_PAGE < goal.getCommands().size()) {
            inventory.setItem(Slots.NEXT_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Component.text("§eNext Page")
            ));
        }

        inventory.setItem(Slots.ADD_COMMAND, parentGui.createGuiItem(
                Material.EMERALD,
                Component.text("§a§lAdd Command"),
                Component.text("§7Click to add a new command")
        ));

        inventory.setItem(Slots.BACK, parentGui.createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("§6§lBack")
        ));

        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete all"),
                Component.text("§7Click to discard every command")
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof CommandsGui) {
            if ((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);
                CommandsGui gui = (CommandsGui) e.getInventory().getHolder();
                gui.handleGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e.getAction());
            } else {
                if (e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    private void handleGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryAction action) {
        if (clickedItem == null || clickedItem.getType().equals(Material.AIR)
                || clickedItem.getType().equals(Material.BLACK_STAINED_GLASS_PANE)) {
            return;
        }

        switch (slot) {
            case Slots.PREV_PAGE -> {
                if (currentPage > 0) {
                    currentPage--;
                    updateCommandsPage();
                }
            }
            case Slots.NEXT_PAGE -> {
                if ((currentPage + 1) * COMMANDS_PER_PAGE < goal.getCommands().size()) {
                    currentPage++;
                    updateCommandsPage();
                }
            }
            case Slots.ADD_COMMAND -> {
                whoClicked.closeInventory();
                startCommandAdd(whoClicked);
            }
            case Slots.BACK -> {
                whoClicked.closeInventory();
                parentGui.openInventory(whoClicked);
            }
            case Slots.DELETE_ALL -> {
                handleDeleteAll(whoClicked);
            }
            default -> {
                if (slot < COMMANDS_PER_PAGE && clickedItem.getType() == Material.PAPER) {
                    String command = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName()).substring(2);
                    if (action.equals(InventoryAction.PICKUP_HALF)) {
                        goal.removeCommand(command);
                        updateCommandsPage();
                    } else {
                        whoClicked.closeInventory();
                        startCommandEdit(whoClicked, command);
                    }
                }
            }
        }
    }

    private void startCommandAdd(Player player) {
        chatHandler.startChatInput(
                player,
                """

                        §7Enter a command with '/' or type §ccancel§7 to exit.
                        You can use the §ePLAYER_NAME §7placeholder to refer to the player.""",
                (p, input) -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(Component.text("§7Command add cancelled"));
                        openInventory(p);
                        return;
                    }
                    goal.addCommand(input);
                    p.sendMessage(Component.text("§7Command §e" + input + " §7added. It will be executed when a player reaches the goal §e" + goal.getName()));
                    openInventory(p);
                }
        );
    }

    private void startCommandEdit(Player player, String oldCommand) {
        chatHandler.startChatInput(
                player,
                """

                        §7Please type the new command in chat or type §ccancel§7 to exit.
                        You can also type §e/cancel §7to exit the process anytime.""",
                (p, input) -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(Component.text("§cCommand edit cancelled"));
                        openInventory(p);
                        return;
                    }

                    goal.removeCommand(oldCommand);
                    goal.addCommand(input);
                    p.sendMessage(Component.text("§aCommand edited successfully!"));
                    openInventory(p);
                }
        );


        Component preText = Component.text("You can ")
                .color(TextColor.color(170,170,170));  // Gray color

        Component clickableText = Component.text("[click here]")
                .color(TextColor.color(255,170,0))  // Gold color
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(oldCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete command")));

        Component fullMessage = Component.empty()
                .append(Component.text("\n"))
                .append(preText)  // Added "You can" part
                .append(clickableText)
                .append(Component.text(" to autocomplete the old command")
                        .color(TextColor.color(170,170,170)));  // Gray color

        player.sendMessage(fullMessage);
    }

    private void handleDeleteAll(Player whoClicked) {
        ItemStack warningItem = parentGui.createGuiItem(
                Material.BARRIER,
                Component.text("§c§lDelete All Commands"),
                Component.text("§7This will remove all commands from this goal")
        );

        ConfirmationGui confirmationGui = new ConfirmationGui(warningItem, (confirmed) -> {
            if (confirmed) {
                for(String cmd: new ArrayList<>(goal.getCommands())) {
                    goal.removeCommand(cmd);
                }
                openInventory(whoClicked);
            } else {
                openInventory(whoClicked);
            }
        });

        whoClicked.closeInventory();
        confirmationGui.openInventory(whoClicked);
    }
}
