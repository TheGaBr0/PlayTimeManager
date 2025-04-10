package me.thegabro.playtimemanager.GUIs.JoinStreak;

import me.thegabro.playtimemanager.Events.ChatEventManager;
import me.thegabro.playtimemanager.GUIs.ConfirmationGui;
import me.thegabro.playtimemanager.JoinStreaks.JoinStreakReward;
import me.thegabro.playtimemanager.Utils;
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

public class JoinStreakCommandsGui implements InventoryHolder, Listener {
    private static final int GUI_SIZE = 54;
    private static final int COMMANDS_PER_PAGE = 45;

    private Inventory inventory;
    private JoinStreakReward reward;
    private JoinStreakRewardSettingsGui parentGui;
    private int currentPage = 0;
    private final ChatEventManager chatEventManager = ChatEventManager.getInstance();

    private static final class Slots {
        static final int PREV_PAGE = 45;
        static final int NEXT_PAGE = 53;
        static final int ADD_COMMAND = 48;
        static final int BACK = 49;
        static final int DELETE_ALL = 50;
    }

    public JoinStreakCommandsGui() {}

    public JoinStreakCommandsGui(JoinStreakReward reward, JoinStreakRewardSettingsGui parentGui) {
        this.reward = reward;
        this.parentGui = parentGui;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, Utils.parseColors("&6Commands Editor"));
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
                Utils.parseColors("&f")
        );
    }

    private void updateCommandsPage() {
        List<String> commands = reward.getCommands();
        int startIndex = currentPage * COMMANDS_PER_PAGE;

        for (int i = 0; i < COMMANDS_PER_PAGE; i++) {
            inventory.setItem(i, createBackgroundItem());
        }

        for (int i = 0; i < COMMANDS_PER_PAGE && (startIndex + i) < commands.size(); i++) {
            String command = commands.get(startIndex + i);
            inventory.setItem(i, parentGui.createGuiItem(
                    Material.PAPER,
                    Utils.parseColors("&e" + command),
                    Utils.parseColors("&7Click to edit"),
                    Utils.parseColors("&cRight-click to remove")
            ));
        }

        addControlButtons();
    }

    private void addControlButtons() {
        if (currentPage > 0) {
            inventory.setItem(Slots.PREV_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Utils.parseColors("&ePrevious Page")
            ));
        }

        if ((currentPage + 1) * COMMANDS_PER_PAGE < reward.getCommands().size()) {
            inventory.setItem(Slots.NEXT_PAGE, parentGui.createGuiItem(
                    Material.ARROW,
                    Utils.parseColors("&eNext Page")
            ));
        }

        inventory.setItem(Slots.ADD_COMMAND, parentGui.createGuiItem(
                Material.EMERALD,
                Utils.parseColors("&a&lAdd Command"),
                Utils.parseColors("&7Click to add a new command")
        ));

        inventory.setItem(Slots.BACK, parentGui.createGuiItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Utils.parseColors("&6&lBack")
        ));

        inventory.setItem(Slots.DELETE_ALL, parentGui.createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete all"),
                Utils.parseColors("&7Click to discard every command")
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof JoinStreakCommandsGui) {
            if ((e.getRawSlot() < e.getInventory().getSize())) {
                e.setCancelled(true);
                JoinStreakCommandsGui gui = (JoinStreakCommandsGui) e.getInventory().getHolder();
                gui.handleGUIClick((Player)e.getWhoClicked(), e.getRawSlot(), e.getCurrentItem(), e);
            } else {
                if (e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    private void handleGUIClick(Player whoClicked, int slot, ItemStack clickedItem, InventoryClickEvent event) {
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
                if ((currentPage + 1) * COMMANDS_PER_PAGE < reward.getCommands().size()) {
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
                    String command = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
                    if (event.isShiftClick() && event.isRightClick()) {
                        reward.removeCommand(command);
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
        // Create messages using Utils.parseColors for the header and instructions
        player.sendMessage(Utils.parseColors("&6&l✎ Commands Editor: &eReward " + reward.getId()));
        player.sendMessage(Utils.parseColors("&r&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(Utils.parseColors("&fEnter the new command for this reward."));
        player.sendMessage(Utils.parseColors("&7• Commands must be valid and use '/' as a prefix."));
        player.sendMessage(Utils.parseColors("&7• Available placeholders: PLAYER_NAME"));
        player.sendMessage(Utils.parseColors("&7• Type &c&icancel&r&7 to exit"));
        player.sendMessage(Utils.parseColors("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        chatEventManager.startCommandInput(
                player,
                (p, input) -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(Utils.parseColors("&7Command add cancelled"));
                        openInventory(p);
                        return;
                    }

                    reward.addCommand(input);

                    String joinRequirement;
                    if (reward.isSingleJoinReward()) {
                        joinRequirement = "&e" + reward.getMinRequiredJoins() + " &7times";
                    } else {
                        joinRequirement = "between &e" + reward.getMinRequiredJoins() +
                                " &7and &e" + reward.getMaxRequiredJoins() + " &7times";
                    }

                    p.sendMessage(Utils.parseColors("&7Command &e" + input + " &7added. It will be executed when " +
                            "a player joins " + joinRequirement));
                    openInventory(p);
                }
        );
    }

    private void startCommandEdit(Player player, String oldCommand) {
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

        // For the clickable message part, we need to keep the Component approach
        // since Utils.parseColors doesn't support clickable text
        Component preText = Component.text("You can ")
                .color(TextColor.color(170,170,170));  // Gray color

        Component clickableText = Component.text("[click here]")
                .color(TextColor.color(255,170,0))  // Gold color
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.suggestCommand(oldCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to autocomplete command")));

        Component fullMessage = Component.empty()
                .append(Component.text("\n"))
                .append(preText)
                .append(clickableText)
                .append(Component.text(" to autocomplete the old command")
                        .color(TextColor.color(170,170,170)));  // Gray color

        player.sendMessage(fullMessage);
    }

    private void handleDeleteAll(Player whoClicked) {
        ItemStack warningItem = parentGui.createGuiItem(
                Material.BARRIER,
                Utils.parseColors("&c&lDelete All Commands"),
                Utils.parseColors("&7This will remove all commands from this reward")
        );

        ConfirmationGui confirmationGui = new ConfirmationGui(warningItem, (confirmed) -> {
            if (confirmed) {
                for(String cmd: new ArrayList<>(reward.getCommands())) {
                    reward.removeCommand(cmd);
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